package com.nimbly.phshoesbackend.useraccount.web;

import com.nimbly.phshoesbackend.useraccount.core.exception.AccountLockedException;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.config.props.LockoutProps;
import com.nimbly.phshoesbackend.useraccount.core.exception.*;
import com.nimbly.phshoesbackend.commons.core.api.rate.RateLimitExceededException;
import com.nimbly.phshoesbackend.useraccounts.model.ErrorResponse;
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
                    .add(Optional.ofNullable(fe.getDefaultMessage())
                            .orElse(msg("error.request.typeMismatch", fe.getField())));
        }
        ErrorResponse body = new ErrorResponse("VALIDATION_ERROR", msg("error.validation.body"));
        body.setDetails(errors);
        return body;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleEmailConflict(EmailAlreadyRegisteredException ex) {
        ErrorResponse body = new ErrorResponse("EMAIL_CONFLICT", msg("error.email.alreadyRegistered"));
        body.setDetails(Map.of("email", List.of(msg("error.email.alreadyRegistered"))));
        return body;
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidToken(InvalidVerificationTokenException ex) {
        ErrorResponse body = new ErrorResponse("INVALID_VERIFICATION_TOKEN", msg("error.verification.invalid"));
        body.setDetails(Map.of("verification", List.of(msg("error.verification.invalid"))));
        return body;
    }

    @ExceptionHandler(VerificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleVerificationNotFound(VerificationNotFoundException ex) {
        ErrorResponse body = new ErrorResponse("VERIFICATION_NOT_FOUND", msg("error.verification.notFound"));
        body.setDetails(Map.of("verification", List.of(msg("error.verification.notFound"))));
        return body;
    }

    @ExceptionHandler(VerificationExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public ErrorResponse handleVerificationExpired(VerificationExpiredException ex) {
        ErrorResponse body = new ErrorResponse("VERIFICATION_EXPIRED", msg("error.verification.expired"));
        body.setDetails(Map.of("verification", List.of(msg("error.verification.expired"))));
        return body;
    }

    @ExceptionHandler(VerificationAlreadyUsedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleVerificationUsed(VerificationAlreadyUsedException ex) {
        ErrorResponse body = new ErrorResponse("VERIFICATION_USED", msg("error.verification.used"));
        body.setDetails(Map.of("verification", List.of(msg("error.verification.used"))));
        return body;
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequestParams(Exception ex) {
        if (ex instanceof MissingServletRequestParameterException mse) {
            ErrorResponse body = new ErrorResponse("BAD_REQUEST", msg("error.request.missingParam", mse.getParameterName()));
            body.setDetails(Map.of("request", List.of(msg("error.request.missingParam", mse.getParameterName()))));
            return body;
        }
        if (ex instanceof MethodArgumentTypeMismatchException mme) {
            ErrorResponse body = new ErrorResponse("BAD_REQUEST", msg("error.request.typeMismatch", mme.getName()));
            body.setDetails(Map.of("request", List.of(msg("error.request.typeMismatch", mme.getName()))));
            return body;
        }
        ErrorResponse body = new ErrorResponse("BAD_REQUEST", msg("error.request.typeMismatch", "request"));
        body.setDetails(Map.of("request", List.of(msg("error.request.typeMismatch", "request"))));
        return body;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        List<String> msgs = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        ErrorResponse body = new ErrorResponse("CONSTRAINT_VIOLATION",
                msgs.isEmpty() ? msg("error.request.typeMismatch", "request") : msg("error.validation.query"));
        body.setDetails(Map.of("request", msgs.isEmpty() ? List.of(msg("error.request.typeMismatch", "request")) : msgs));
        return body;
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoHandler(NoHandlerFoundException ex) {
        ErrorResponse body = new ErrorResponse("NOT_FOUND", msg("error.request.noHandler", ex.getRequestURL()));
        body.setDetails(Map.of("path", List.of(msg("error.request.noHandler", ex.getRequestURL()))));
        return body;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentials(InvalidCredentialsException ex) {
        ErrorResponse body = new ErrorResponse("INVALID_CREDENTIALS", msg("error.auth.invalidCredentials"));
        body.setDetails(Map.of("credentials", List.of(msg("error.auth.invalidCredentials"))));
        return body;
    }

    @ExceptionHandler(AccountBlockedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAccountBlockedException(AccountBlockedException ex) {
        ErrorResponse body = new ErrorResponse("ACCOUNT_BLOCKED", msg("error.email.blocked"));
        body.setDetails(Map.of("account", List.of(msg("error.email.blocked"))));
        return body;
    }

    @ExceptionHandler(AccountLockedException.class)
    @ResponseStatus(HttpStatus.LOCKED)
    public ErrorResponse handleAccountLocked(AccountLockedException ex) {
        int minutes = Math.max(1, (int) Math.ceil(lockProps.getDurationSeconds() / 60.0));
        ErrorResponse body = new ErrorResponse("ACCOUNT_LOCKED", msg("error.auth.accountLocked", minutes));
        body.setDetails(Map.of("account", List.of(msg("error.auth.accountLocked", minutes))));
        return body;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleAccountNotFound(Exception ex) {
        ErrorResponse body = new ErrorResponse("ACCOUNT_NOT_FOUND", msg("error.account.notFound"));
        body.setDetails(Map.of("account", List.of(msg("error.account.notFound"))));
        return body;
    }

    @ExceptionHandler(SessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSessionNotFound(Exception ex) {
        ErrorResponse body = new ErrorResponse("SESSION_NOT_FOUND", msg("error.session.notFound"));
        body.setDetails(Map.of("session", List.of(msg("error.session.notFound"))));
        return body;
    }

    @ExceptionHandler(UserAccountNotificationSendException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleNotificationSendError(Exception ex) {
        ErrorResponse body = new ErrorResponse("NOTIFICATION_SEND_FAILED", msg("error.notification.sendFailed"));
        body.setDetails(Map.of("notification", List.of(msg("error.notification.sendFailed"))));
        return body;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ErrorResponse handleRateLimitExceeded(RateLimitExceededException ex) {
        ErrorResponse body = new ErrorResponse("RATE_LIMITED", msg("error.rateLimit.tooMany"));
        body.setDetails(Map.of(
                "scope", List.of(Optional.ofNullable(ex.getScope()).orElse("unknown")),
                "rateLimit", List.of(msg("error.rateLimit.tooMany"))
        ));
        return body;
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
        log.error("traceId={} method={} uri={} msg={}", traceId, req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        Map<String, List<String>> details = new LinkedHashMap<>();
        details.put("global", List.of(msg("error.global.tryAgain")));
        details.put("traceId", List.of(traceId));
        ErrorResponse body = new ErrorResponse("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        body.setDetails(details);
        return body;
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleEmailNotVerified(Exception ex) {
        ErrorResponse body = new ErrorResponse("EMAIL_NOT_VERIFIED", msg("error.auth.emailNotVerified"));
        body.setDetails(Map.of("verification", List.of(msg("error.auth.emailNotVerified"))));
        return body;
    }
}
