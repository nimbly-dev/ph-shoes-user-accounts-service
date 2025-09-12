package com.nimbly.phshoesbackend.useraccount.web;

import com.nimbly.phshoesbackend.useraccount.exception.*;
import com.nimbly.phshoesbackend.useraccount.model.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.validation.FieldError;

import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBodyValidation(MethodArgumentNotValidException ex,
                                              HttpServletRequest req) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fe.getField(), k -> new ArrayList<>())
                    .add(Optional.ofNullable(fe.getDefaultMessage()).orElse("Invalid value"));
        }
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                errors
        );
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleEmailConflict(EmailAlreadyRegisteredException ex) {
        Map<String, List<String>> errors = Map.of(
                "email", List.of(Optional.ofNullable(ex.getMessage()).orElse("Email already registered"))
        );
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                errors
        );
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidToken(InvalidVerificationTokenException ex) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                Map.of("token", List.of("Invalid verification token"))
        );
    }

    @ExceptionHandler(VerificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleVerificationNotFound(VerificationNotFoundException ex) {
        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                Map.of("verification", List.of("Verification was not found"))
        );
    }

    @ExceptionHandler(VerificationExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public ErrorResponse handleVerificationExpired(VerificationExpiredException ex) {
        return new ErrorResponse(
                HttpStatus.GONE.value(),
                HttpStatus.GONE.getReasonPhrase(),
                Map.of("verification", List.of("Verification has expired"))
        );
    }

    @ExceptionHandler(VerificationAlreadyUsedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleVerificationUsed(VerificationAlreadyUsedException ex) {
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                Map.of("verification", List.of("Verification already used"))
        );
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequestParams(Exception ex) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                Map.of("request", List.of("Bad or missing request parameters"))
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        List<String> msgs = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                Map.of("request", msgs.isEmpty() ? List.of("Invalid request") : msgs)
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoHandler(NoHandlerFoundException ex) {
        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                Map.of("path", List.of("No handler for " + ex.getRequestURL()))
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAny(Exception ex, HttpServletRequest req) {
        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                Map.of("global", List.of(
                        Optional.ofNullable(ex.getMessage()).orElse("Internal server error")))
        );
    }
}
