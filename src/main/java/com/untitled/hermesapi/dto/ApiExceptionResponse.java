package com.untitled.hermesapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiExceptionResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) {
    public ApiExceptionResponse(int status, String code, String message) {
        this(LocalDateTime.now(), status, code, message, null);
    }
}
