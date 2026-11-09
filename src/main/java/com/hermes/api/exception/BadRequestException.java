package com.hermes.api.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class BadRequestException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
