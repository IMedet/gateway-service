package kz.qonaqzhai.gatewayservice.config;

import java.io.Serial;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnAuthorizeException extends RuntimeException{
    
    @Serial
    private static final long serialVersionUID = 1L;

    private final String message;

    public UnAuthorizeException(String message){
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    

}

