package kz.qonaqzhai.gatewayservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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


            return webClient.get()
                    .uri("/api/validate?token=" + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMap(valid -> {
                        if ("false".equals(valid)) {
                            log.error("Token failed validation");
                            return Mono.error(new UnAuthorizeException("Invalid token"));
                        }
                        exchange.getRequest().mutate().headers(httpHeaders ->
                                httpHeaders.add("username", String.valueOf(valid))
                        );
                        return chain.filter(exchange);
                    })
                    .onErrorResume(error -> {
                        log.error("Error during Request ");
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
