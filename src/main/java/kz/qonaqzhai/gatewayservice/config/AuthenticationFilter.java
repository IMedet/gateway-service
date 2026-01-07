package kz.qonaqzhai.gatewayservice.config;

import kz.qonaqzhai.gatewayservice.dto.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends AbstractGatewayFilterFactory<Object> {

    private final WebClient webClient;

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                throw new UnAuthorizeException("Missing authorization header");
            }
            String token = resolveToken(exchange);

            if (!StringUtils.hasText(token)) {
                throw new UnAuthorizeException("Invalid authorization header");
            }


            return webClient.post() // 1. POST қолдану
                    .uri("/api/auth/validate") // 2. Дұрыс URI
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED) // 3. Форм деректерін жіберу үшін
                    .body(BodyInserters.fromFormData("token", token)) // 4. Токенді форма параметрі ретінде жіберу
                    .retrieve()
                    .bodyToMono(MessageResponse.class) // 5. Жауапты MessageResponse ретінде қабылдау
                    .flatMap(response -> {
                        String message = response.getMessage(); // 6. Жауаптан сообщение алу
                        if (message.contains("Invalid token")) { // 7. Жарамсыз токен шартын өзгерту
                            log.error("Token failed validation: {}", message);
                            return Mono.error(new UnAuthorizeException("Invalid token"));
                        }
                        // Жауап форматы: "Token is valid for user: username"
                        // Бұл жерде message-ден username алу керек
                        String usernamePrefix = "Token is valid for user: ";
                        if (message.startsWith(usernamePrefix)) {
                            String username = message.substring(usernamePrefix.length());
                            ServerHttpRequest mutatedRequest = exchange.getRequest()
                                    .mutate()
                                    .header("username", username)
                                    .build();
                            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
                            return chain.filter(mutatedExchange);
                        } else {
                            // Егер формат күтілгендей болмаса, қате лақтырамыз
                            log.error("Unexpected validation response format: {}", message);
                            return Mono.error(new UnAuthorizeException("Invalid validation response format"));
                        }
                    })
                    .onErrorResume(error -> {
                        log.error("Error during Request ", error); // 9. Қате логын жақсарту
                        return Mono.error(error);
                    });
        };
    }

    public String resolveToken(ServerWebExchange req) {
        String bearerToken = Objects.requireNonNull(
                req.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION)
        ).get(0);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
