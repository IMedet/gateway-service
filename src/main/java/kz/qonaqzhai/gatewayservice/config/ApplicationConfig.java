package kz.qonaqzhai.gatewayservice.config;

import kz.qonaqzhai.gatewayservice.security.InternalRequestSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    @Value("${auth-service.url}")
    private String authServiceUrl;

    @Bean
    public InternalRequestSigner internalRequestSigner(InternalGatewaySignatureProperties props) {
        return new InternalRequestSigner(props);
    }

    @Bean
    public WebClient webClient(InternalRequestSigner signer) {
        ExchangeFilterFunction internalSigningFilter = (request, next) -> {
            if (!signer.isEnabled()) {
                return next.exchange(request);
            }

            String method = request.method().name();
            String fullPath = request.url().getRawPath();
            String rawQuery = request.url().getRawQuery();
            if (rawQuery != null && !rawQuery.isBlank()) {
                fullPath = fullPath + "?" + rawQuery;
            }

            String ts = String.valueOf(Instant.now().toEpochMilli());
            String sig = signer.sign(method, fullPath, ts);

            ClientRequest signed = ClientRequest.from(request)
                    .header(signer.timestampHeaderName(), ts)
                    .header(signer.signatureHeaderName(), sig)
                    .build();

            return next.exchange(signed);
        };

        return WebClient.builder()
                .baseUrl(authServiceUrl)
                .filter(internalSigningFilter)
                .build();
    }
}
