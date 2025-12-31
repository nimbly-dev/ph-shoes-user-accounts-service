package com.nimbly.phshoesbackend.useraccount.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserAccountExceptionsTest {

    @Test
    void accountBlockedException_constructors() {
        // Arrange
        AccountBlockedException empty = new AccountBlockedException();
        AccountBlockedException withMessage = new AccountBlockedException("blocked");

        // Assert
        assertNull(empty.getMessage());
        assertEquals("blocked", withMessage.getMessage());
    }

    @Test
    void accountNotFoundException_constructors() {
        // Arrange
        AccountNotFoundException empty = new AccountNotFoundException();
        AccountNotFoundException withMessage = new AccountNotFoundException("missing");

        // Assert
        assertNull(empty.getMessage());
        assertEquals("missing", withMessage.getMessage());
    }

    @Test
    void emailAlreadyRegisteredException_constructors() {
        // Arrange
        EmailAlreadyRegisteredException empty = new EmailAlreadyRegisteredException();
        EmailAlreadyRegisteredException withMessage = new EmailAlreadyRegisteredException("exists");

        // Assert
        assertEquals("Email already registered", empty.getMessage());
        assertEquals("exists", withMessage.getMessage());
    }

    @Test
    void emailNotVerifiedException_constructors() {
        // Arrange
        EmailNotVerifiedException empty = new EmailNotVerifiedException();
        EmailNotVerifiedException withMessage = new EmailNotVerifiedException("verify");

        // Assert
        assertNull(empty.getMessage());
        assertEquals("verify", withMessage.getMessage());
    }

    @Test
    void invalidVerificationTokenException_constructor() {
        // Arrange
        InvalidVerificationTokenException exception = new InvalidVerificationTokenException("bad");

        // Assert
        assertEquals("bad", exception.getMessage());
    }

    @Test
    void sessionNotFoundException_constructors() {
        // Arrange
        SessionNotFoundException empty = new SessionNotFoundException();
        SessionNotFoundException withMessage = new SessionNotFoundException("missing");

        // Assert
        assertNull(empty.getMessage());
        assertEquals("missing", withMessage.getMessage());
    }

    @Test
    void userAccountNotificationSendException_constructors() {
        // Arrange
        RuntimeException cause = new RuntimeException("cause");
        UserAccountNotificationSendException withCause = new UserAccountNotificationSendException("send", cause);
        UserAccountNotificationSendException withMessage = new UserAccountNotificationSendException("send");

        // Assert
        assertEquals("send", withCause.getMessage());
        assertEquals(cause, withCause.getCause());
        assertEquals("send", withMessage.getMessage());
    }

    @Test
    void verificationAlreadyUsedException_constructor() {
        // Arrange
        VerificationAlreadyUsedException exception = new VerificationAlreadyUsedException("used");

        // Assert
        assertEquals("used", exception.getMessage());
    }

    @Test
    void verificationExpiredException_constructor() {
        // Arrange
        VerificationExpiredException exception = new VerificationExpiredException("expired");

        // Assert
        assertEquals("expired", exception.getMessage());
    }

    @Test
    void verificationNotFoundException_constructor() {
        // Arrange
        VerificationNotFoundException exception = new VerificationNotFoundException("missing");

        // Assert
        assertEquals("missing", exception.getMessage());
    }
}
