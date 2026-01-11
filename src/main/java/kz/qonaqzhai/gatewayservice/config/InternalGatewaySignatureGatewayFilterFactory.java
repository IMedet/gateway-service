package kz.qonaqzhai.gatewayservice.config;

import kz.qonaqzhai.gatewayservice.security.InternalRequestSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class InternalGatewaySignatureGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private final InternalRequestSigner signer;

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            if (!signer.isEnabled()) {
                return chain.filter(exchange);
            }

            ServerHttpRequest request = exchange.getRequest();
            String method = request.getMethod() != null ? request.getMethod().name() : "GET";
            String rawPath = request.getURI().getRawPath();
            String rawQuery = request.getURI().getRawQuery();
            String fullPath = (rawQuery == null || rawQuery.isBlank()) ? rawPath : (rawPath + "?" + rawQuery);

            String ts = String.valueOf(Instant.now().toEpochMilli());
            String sig = signer.sign(method, fullPath, ts);

            ServerHttpRequest mutated = request.mutate()
                    .header(signer.timestampHeaderName(), ts)
                    .header(signer.signatureHeaderName(), sig)
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();
            return chain.filter(mutatedExchange);
        };
    }
}
