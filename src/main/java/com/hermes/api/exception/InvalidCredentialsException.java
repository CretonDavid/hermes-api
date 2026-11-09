package com.hermes.api.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", message);
    }
}
