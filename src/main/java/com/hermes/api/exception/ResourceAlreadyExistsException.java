package com.hermes.api.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class ResourceAlreadyExistsException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, "ALREADY_EXISTS", message);
    }
}
