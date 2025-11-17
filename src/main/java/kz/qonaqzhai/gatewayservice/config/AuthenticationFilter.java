package kz.qonaqzhai.gatewayservice.config;

import kz.qonaqzhai.gatewayservice.dto.ValidationResponse;
import kz.qonaqzhai.gatewayservice.exception.UnAuthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends AbstractGatewayFilterFactory<Object> {

    private final WebClient webClient;


    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().toString();

            log.debug("Processing authentication for path: {}", path);

            // Проверка наличия заголовка Authorization
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                log.warn("Missing authorization header for path: {}", path);
                return Mono.error(new UnAuthorizedException("Missing authorization header"));
            }

            // Извлечение токена
            String token;
            try {
                token = resolveToken(exchange);
                if (!StringUtils.hasText(token)) {
                    log.warn("Invalid Bearer token format for path: {}", path);
                    return Mono.error(new UnAuthorizedException("Invalid Bearer token format"));
                }
            } catch (Exception e) {
                log.error("Error extracting token for path: {}", path, e);
                return Mono.error(new UnAuthorizedException("Error extracting token"));
            }

            log.debug("Validating token for path: {}", path);

            // Валидация токена через внешний сервис
            return validateToken(token)
                    .flatMap(validationResponse -> {
                        if (!validationResponse.isValid()) {
                            log.error("Token validation failed for path: {}. Reason: {}",
                                    path, validationResponse.getMessage());
                            return Mono.error(new UnAuthorizedException(
                                    "Invalid token: " + validationResponse.getMessage()));
                        }

                        log.info("Token validated successfully for user: {} on path: {}",
                                validationResponse.getUsername(), path);

                        // Создание нового запроса с добавленным заголовком username
                        ServerHttpRequest mutatedRequest = exchange.getRequest()
                                .mutate()
                                .header("X-User-Name", validationResponse.getUsername())
                                .build();

                        // Создание нового exchange с измененным запросом
                        ServerWebExchange mutatedExchange = exchange.mutate()
                                .request(mutatedRequest)
                                .build();

                        return chain.filter(mutatedExchange);
                    })
                    .onErrorResume(UnAuthorizedException.class, error -> {
                        log.error("Authorization error for path: {}", path, error);
                        return Mono.error(error);
                    })
                    .onErrorResume(WebClientResponseException.class, error -> {
                        log.error("Validation service error for path: {}. Status: {}",
                                path, error.getStatusCode(), error);
                        return Mono.error(new UnAuthorizedException(
                                "Token validation service unavailable", error));
                    })
                    .onErrorResume(error -> {
                        log.error("Unexpected error during authentication for path: {}", path, error);
                        return Mono.error(new UnAuthorizedException(
                                "Authentication failed: " + error.getMessage(), error));
                    });
        };
    }

    /**
     * Валидация токена через внешний сервис
     *
     * @param token JWT токен
     * @return ValidationResponse с результатом валидации
     */
    private Mono<ValidationResponse> validateToken(String token) {
        return webClient.post()
                .uri("/api/validate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(
                        status -> status.value() == HttpStatus.UNAUTHORIZED.value(),
                        response -> Mono.just(new UnAuthorizedException("Invalid token"))
                )
                .bodyToMono(ValidationResponse.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(error -> {
                    log.error("Error calling validation service", error);
                    return Mono.just(new ValidationResponse(false, null,
                            "Validation service error: " + error.getMessage()));
                });
    }


    /**
     * Извлечение JWT токена из заголовка Authorization
     *
     * @param exchange ServerWebExchange
     * @return JWT токен без префикса "Bearer "
     * @throws IllegalArgumentException если заголовок отсутствует или имеет неверный формат
     */
    private String resolveToken(ServerWebExchange exchange) {
        List<String> authHeaders = exchange.getRequest()
                .getHeaders()
                .get(HttpHeaders.AUTHORIZATION);

        if (authHeaders == null || authHeaders.isEmpty()) {
            throw new IllegalArgumentException("Authorization header is missing");
        }

        String bearerToken = authHeaders.get(0);

        if (!StringUtils.hasText(bearerToken)) {
            throw new IllegalArgumentException("Authorization header is empty");
        }

        if (!bearerToken.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header must start with 'Bearer '");
        }

        String token = bearerToken.substring(7).trim();

        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Token is empty after 'Bearer ' prefix");
        }

        return token;
    }
}
