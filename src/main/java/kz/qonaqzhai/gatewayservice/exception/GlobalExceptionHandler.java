package kz.qonaqzhai.gatewayservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@Order(-2)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {
        if (ex instanceof UnAuthorizedException) {
            return handleUnAuthorizedException(exchange, (UnAuthorizedException) ex);
        }
        
        return handleGenericException(exchange, ex);
    }
    
    private Mono<Void> handleUnAuthorizedException(ServerWebExchange exchange, UnAuthorizedException ex) {
        log.error("Unauthorized access attempt: {}", ex.getMessage());
        
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String errorMessage = String.format(
                "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"status\":401}",
                ex.getMessage()
        );
        
        byte[] bytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
    
    private Mono<Void> handleGenericException(ServerWebExchange exchange, Throwable ex) {
        log.error("Unexpected error occurred", ex);
        
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String errorMessage = String.format(
                "{\"error\":\"Internal Server Error\",\"message\":\"%s\",\"status\":500}",
                ex.getMessage()
        );
        
        byte[] bytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
