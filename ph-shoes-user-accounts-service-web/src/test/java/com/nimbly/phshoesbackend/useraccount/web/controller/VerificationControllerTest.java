package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.nimbly.phshoesbackend.useraccount.core.exception.UserAccountNotificationSendException;
import com.nimbly.phshoesbackend.useraccount.core.exception.VerificationAlreadyUsedException;
import com.nimbly.phshoesbackend.useraccount.core.verification.VerificationService;
import com.nimbly.phshoesbackend.useraccounts.model.ResendVerificationEmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationControllerTest {

    @Mock
    private VerificationService verificationService;

    private VerificationController controller;

    @BeforeEach
    void setUp() {
        controller = new VerificationController(verificationService);
        ReflectionTestUtils.setField(controller, "frontendBaseUrl", "https://frontend.example");
        ReflectionTestUtils.setField(controller, "frontendVerifyPath", "/verify");
    }

    @Test
    void verifyEmail_redirectsWhenTokenMissing() {
        // Arrange
        String token = " ";

        // Act
        ResponseEntity<Void> response = controller.verifyEmail(token);

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=missing_token", response.getHeaders().getLocation().toString());
    }

    @Test
    void verifyEmail_redirectsWhenAlreadyUsed() {
        // Arrange
        when(verificationService.verify("token")).thenThrow(new VerificationAlreadyUsedException("used"));

        // Act
        ResponseEntity<Void> response = controller.verifyEmail("token");

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=already_used", response.getHeaders().getLocation().toString());
    }

    @Test
    void verifyEmail_redirectsWhenInvalidToken() {
        // Arrange
        when(verificationService.verify("token")).thenThrow(new IllegalArgumentException("bad"));

        // Act
        ResponseEntity<Void> response = controller.verifyEmail("token");

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=invalid_token", response.getHeaders().getLocation().toString());
    }

    @Test
    void verifyEmail_redirectsWhenUnexpectedError() {
        // Arrange
        when(verificationService.verify("token")).thenThrow(new RuntimeException("boom"));

        // Act
        ResponseEntity<Void> response = controller.verifyEmail("token");

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=unexpected", response.getHeaders().getLocation().toString());
    }

    @Test
    void verifyEmail_redirectsWhenVerified() {
        // Arrange
        when(verificationService.verify("token")).thenReturn(true);

        // Act
        ResponseEntity<Void> response = controller.verifyEmail("token");

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?verified=true", response.getHeaders().getLocation().toString());
    }

    @Test
    void resendVerificationEmail_redirectsWhenMissingEmail() {
        // Arrange
        ResendVerificationEmailRequest request = new ResendVerificationEmailRequest();

        // Act
        ResponseEntity<Void> response = controller.resendVerificationEmail(request);

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=missing_email", response.getHeaders().getLocation().toString());
    }

    @Test
    void resendVerificationEmail_redirectsWhenSuccessful() {
        // Arrange
        ResendVerificationEmailRequest request = new ResendVerificationEmailRequest();
        request.setEmail("user@example.com");

        // Act
        ResponseEntity<Void> response = controller.resendVerificationEmail(request);

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?resent=true", response.getHeaders().getLocation().toString());
    }

    @Test
    void resendVerificationEmail_redirectsWhenSendFails() {
        // Arrange
        ResendVerificationEmailRequest request = new ResendVerificationEmailRequest();
        request.setEmail("user@example.com");
        doThrow(new UserAccountNotificationSendException("fail"))
                .when(verificationService).sendVerificationEmail("user@example.com");

        // Act
        ResponseEntity<Void> response = controller.resendVerificationEmail(request);

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=send_failed", response.getHeaders().getLocation().toString());
    }

    @Test
    void resendVerificationEmail_redirectsWhenUnexpectedError() {
        // Arrange
        ResendVerificationEmailRequest request = new ResendVerificationEmailRequest();
        request.setEmail("user@example.com");
        doThrow(new RuntimeException("boom"))
                .when(verificationService).sendVerificationEmail("user@example.com");

        // Act
        ResponseEntity<Void> response = controller.resendVerificationEmail(request);

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=unexpected", response.getHeaders().getLocation().toString());
    }

    @Test
    void markEmailNotMe_redirectsWhenMissingToken() {
        // Arrange
        String token = "";

        // Act
        ResponseEntity<Void> response = controller.markEmailNotMe(token);

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=missing_params", response.getHeaders().getLocation().toString());
    }

    @Test
    void markEmailNotMe_redirectsWhenSuccessful() {
        // Arrange
        when(verificationService.notMe("token")).thenReturn(true);

        // Act
        ResponseEntity<Void> response = controller.markEmailNotMe("token");

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?notMe=true", response.getHeaders().getLocation().toString());
    }

    @Test
    void markEmailNotMe_redirectsWhenInvalidToken() {
        // Arrange
        when(verificationService.notMe("token")).thenThrow(new IllegalArgumentException("bad"));

        // Act
        ResponseEntity<Void> response = controller.markEmailNotMe("token");

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=invalid_params", response.getHeaders().getLocation().toString());
    }

    @Test
    void markEmailNotMe_redirectsWhenUnexpectedError() {
        // Arrange
        when(verificationService.notMe("token")).thenThrow(new RuntimeException("boom"));

        // Act
        ResponseEntity<Void> response = controller.markEmailNotMe("token");

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/verify?error=unexpected", response.getHeaders().getLocation().toString());
    }
}
