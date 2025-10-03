package com.nimbly.phshoesbackend.useraccount.web;

import com.nimbly.phshoesbackend.useraccount.auth.exception.AccountLockedException;
import com.nimbly.phshoesbackend.useraccount.auth.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.config.props.LockoutProps;
import com.nimbly.phshoesbackend.useraccount.exception.*;
import com.nimbly.phshoesbackend.useraccount.model.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messages;
    private final LockoutProps lockProps;

    public GlobalExceptionHandler(MessageSource messages, LockoutProps lockProps) {
        this.messages = messages;
        this.lockProps = lockProps;
    }

    private String msg(String key, Object... args) {
        return messages.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fe.getField(), k -> new ArrayList<>())
                    .add(Optional.ofNullable(fe.getDefaultMessage()).orElse(msg("error.request.typeMismatch", fe.getField())));
        }
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), errors);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleEmailConflict(EmailAlreadyRegisteredException ex) {
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                Map.of("email", List.of(msg("error.email.alreadyRegistered")))
        );
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidToken(InvalidVerificationTokenException ex) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                Map.of("verification", List.of(msg("error.verification.invalid")))
        );
    }

    @ExceptionHandler(VerificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleVerificationNotFound(VerificationNotFoundException ex) {
        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                Map.of("verification", List.of(msg("error.verification.notFound")))
        );
    }

    @ExceptionHandler(VerificationExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public ErrorResponse handleVerificationExpired(VerificationExpiredException ex) {
        return new ErrorResponse(
                HttpStatus.GONE.value(),
                HttpStatus.GONE.getReasonPhrase(),
                Map.of("verification", List.of(msg("error.verification.expired")))
        );
    }

    @ExceptionHandler(VerificationAlreadyUsedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleVerificationUsed(VerificationAlreadyUsedException ex) {
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                Map.of("verification", List.of(msg("error.verification.used")))
        );
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequestParams(Exception ex) {
        if (ex instanceof MissingServletRequestParameterException mse) {
            return new ErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    Map.of("request", List.of(msg("error.request.missingParam", mse.getParameterName())))
            );
        }
        if (ex instanceof MethodArgumentTypeMismatchException mme) {
            return new ErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    Map.of("request", List.of(msg("error.request.typeMismatch", mme.getName())))
            );
        }
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                Map.of("request", List.of(msg("error.request.typeMismatch", "request")))
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
                Map.of("request", msgs.isEmpty() ? List.of(msg("error.request.typeMismatch", "request")) : msgs)
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoHandler(NoHandlerFoundException ex) {
        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                Map.of("path", List.of(msg("error.request.noHandler", ex.getRequestURL())))
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentials(InvalidCredentialsException ex) {
        return new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                Map.of("credentials", List.of(msg("error.auth.invalidCredentials")))
        );
    }

    @ExceptionHandler(AccountLockedException.class)
    @ResponseStatus(HttpStatus.LOCKED)
    public ErrorResponse handleAccountLocked(AccountLockedException ex) {
        int minutes = Math.max(1, (int) Math.ceil(lockProps.getDurationSeconds() / 60.0));
        return new ErrorResponse(
                HttpStatus.LOCKED.value(),
                HttpStatus.LOCKED.getReasonPhrase(),
                Map.of("account", List.of(msg("error.auth.accountLocked", minutes)))
        );
    }

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleAccountNotFound(Exception ex) {
        return new ErrorResponse(404, "Not Found", Map.of("account", List.of("Account not found or already deleted")));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSessionNotFound(Exception ex) {
        return new ErrorResponse(404, "Not Found", Map.of("session", List.of("Session not found or already revoked")));
    }


    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAny(Exception ex, HttpServletRequest req) {
        String traceId = Optional.ofNullable(MDC.get("traceId"))
                .orElseGet(() -> {
                    String t = UUID.randomUUID().toString();
                    MDC.put("traceId", t);
                    return t;
                });

        log.error("traceId={} method={} uri={} msg={}",
                traceId, req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);

        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                Map.of("global", List.of("Something went wrong on our side. Please try again."),
                        "traceId", List.of(traceId))
        );
    }
    @ExceptionHandler(EmailNotVerifiedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleEmailNotVerified(Exception ex) {
        return new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                Map.of("verification", List.of("Please verify your email to continue."))
        );
    }

}
