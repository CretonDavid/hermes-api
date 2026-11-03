package com.untitled.hermesapi.exception;

import org.springframework.http.HttpStatus;

public class ResourceAlreadyExistsException extends ApplicationException {

    public ResourceAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, "ALREADY_EXISTS", message);
    }
}
