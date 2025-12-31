package com.nimbly.phshoesbackend.useraccount.web;

import com.nimbly.phshoesbackend.commons.core.api.rate.RateLimitExceededException;
import com.nimbly.phshoesbackend.useraccount.core.exception.AccountLockedException;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.config.props.LockoutProps;
import com.nimbly.phshoesbackend.useraccount.core.exception.AccountBlockedException;
import com.nimbly.phshoesbackend.useraccount.core.exception.EmailAlreadyRegisteredException;
import com.nimbly.phshoesbackend.useraccount.core.exception.EmailNotVerifiedException;
import com.nimbly.phshoesbackend.useraccount.core.exception.VerificationExpiredException;
import com.nimbly.phshoesbackend.useraccounts.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;
    @Mock
    private HttpServletRequest httpServletRequest;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        LockoutProps lockoutProps = new LockoutProps();
        lockoutProps.setDurationSeconds(120);

        when(messageSource.getMessage(anyString(), any(Object[].class), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        handler = new GlobalExceptionHandler(messageSource, lockoutProps);
    }

    @Test
    void handleBodyValidation_buildsErrorResponse() throws Exception {
        // Arrange
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "email", "invalid"));
        Method method = DummyTarget.class.getDeclaredMethod("dummy", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        // Act
        ErrorResponse response = handler.handleBodyValidation(exception, httpServletRequest);

        // Assert
        assertEquals("VALIDATION_ERROR", response.getCode());
        Map<?, ?> details = (Map<?, ?>) response.getDetails();
        assertNotNull(details.get("email"));
    }

    @Test
    void handleInvalidCredentials_returnsUnauthorized() {
        // Arrange
        InvalidCredentialsException exception = new InvalidCredentialsException();

        // Act
        ErrorResponse response = handler.handleInvalidCredentials(exception);

        // Assert
        assertEquals("INVALID_CREDENTIALS", response.getCode());
    }

    @Test
    void handleAccountLocked_returnsLocked() {
        // Arrange
        AccountLockedException exception = new AccountLockedException();

        // Act
        ErrorResponse response = handler.handleAccountLocked(exception);

        // Assert
        assertEquals("ACCOUNT_LOCKED", response.getCode());
    }

    @Test
    void handleAccountBlocked_returnsUnauthorized() {
        // Arrange
        AccountBlockedException exception = new AccountBlockedException("blocked");

        // Act
        ErrorResponse response = handler.handleAccountBlockedException(exception);

        // Assert
        assertEquals("ACCOUNT_BLOCKED", response.getCode());
    }

    @Test
    void handleEmailConflict_returnsConflict() {
        // Arrange
        EmailAlreadyRegisteredException exception = new EmailAlreadyRegisteredException("exists");

        // Act
        ErrorResponse response = handler.handleEmailConflict(exception);

        // Assert
        assertEquals("EMAIL_CONFLICT", response.getCode());
    }

    @Test
    void handleVerificationExpired_returnsGone() {
        // Arrange
        VerificationExpiredException exception = new VerificationExpiredException("expired");

        // Act
        ErrorResponse response = handler.handleVerificationExpired(exception);

        // Assert
        assertEquals("VERIFICATION_EXPIRED", response.getCode());
    }

    @Test
    void handleBadRequestParams_handlesMissingParam() {
        // Arrange
        MissingServletRequestParameterException exception = new MissingServletRequestParameterException("email", "String");

        // Act
        ErrorResponse response = handler.handleBadRequestParams(exception);

        // Assert
        assertEquals("BAD_REQUEST", response.getCode());
    }

    @Test
    void handleBadRequestParams_handlesTypeMismatch() {
        // Arrange
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException("bad", String.class, "email", null, new IllegalArgumentException("bad"));

        // Act
        ErrorResponse response = handler.handleBadRequestParams(exception);

        // Assert
        assertEquals("BAD_REQUEST", response.getCode());
    }

    @Test
    void handleRateLimitExceeded_returnsRateLimited() {
        // Arrange
        RateLimitExceededException exception = new RateLimitExceededException("auth", "slow down");

        // Act
        ErrorResponse response = handler.handleRateLimitExceeded(exception);

        // Assert
        assertEquals("RATE_LIMITED", response.getCode());
    }

    @Test
    void handleEmailNotVerified_returnsForbidden() {
        // Arrange
        EmailNotVerifiedException exception = new EmailNotVerifiedException();

        // Act
        ErrorResponse response = handler.handleEmailNotVerified(exception);

        // Assert
        assertEquals("EMAIL_NOT_VERIFIED", response.getCode());
    }

    @Test
    void handleAny_returnsInternalError() {
        // Arrange
        when(httpServletRequest.getMethod()).thenReturn("GET");
        when(httpServletRequest.getRequestURI()).thenReturn("/api");

        // Act
        ErrorResponse response = handler.handleAny(new RuntimeException("boom"), httpServletRequest);

        // Assert
        assertEquals("INTERNAL_ERROR", response.getCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), response.getMessage());
    }

    static class DummyTarget {
        void dummy(String value) {
        }
    }
}
