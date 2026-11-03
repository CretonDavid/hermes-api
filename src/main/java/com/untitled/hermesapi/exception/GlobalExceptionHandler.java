package com.untitled.hermesapi.exception;

import com.untitled.hermesapi.dto.ApiExceptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiExceptionResponse> handleApplicationException(ApplicationException exception) {
        log.warn("{} : {}", exception.getCode(), exception.getMessage());
        return build(exception.getStatus(), exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiExceptionResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ApiExceptionResponse response = new ApiExceptionResponse(
                LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "Certains champs sont invalides", errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiExceptionResponse> handleBadInput(Exception exception) {
        log.warn("Requête invalide : {}", exception.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Requête invalide");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiExceptionResponse> handleUploadSize(MaxUploadSizeExceededException exception) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "Le fichier est trop volumineux (10 Mo maximum)");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiExceptionResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", exception.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiExceptionResponse> handleNoResource(NoResourceFoundException exception) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ressource introuvable");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiExceptionResponse> handleException(Exception exception) {
        log.error("Erreur inattendue", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Une erreur inattendue est survenue");
    }

    private ResponseEntity<ApiExceptionResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiExceptionResponse(status.value(), code, message));
    }
}
