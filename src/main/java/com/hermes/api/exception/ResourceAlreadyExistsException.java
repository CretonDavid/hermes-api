package com.hermes.api.exception;

import org.springframework.http.HttpStatus;

public class ResourceAlreadyExistsException extends ApplicationException {

    public ResourceAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, "ALREADY_EXISTS", message);
    }
}
