package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.subscribe.SubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccounts.model.SubscriptionStatusResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountsEmailNotificationControllerTest {

    @Mock
    private UnsubscribeService unsubscribeService;
    @Mock
    private SubscribeService subscribeService;
    @Mock
    private SuppressionService suppressionService;

    private UserAccountsEmailNotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new UserAccountsEmailNotificationController(
                unsubscribeService,
                subscribeService,
                suppressionService
        );
        ReflectionTestUtils.setField(controller, "frontendBaseUrl", "https://frontend.example");
        ReflectionTestUtils.setField(controller, "frontendUnsubscribePath", "/unsubscribe");
    }

    @Test
    void unsubscribeOneClick_returnsBadRequestWhenTokenMissing() {
        // Arrange
        String listHeader = "List-Unsubscribe=One-Click";

        // Act
        ResponseEntity<Void> response = controller.unsubscribeOneClick(" ", listHeader);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void unsubscribeOneClick_returnsNoContentWhenSuccessful() {
        // Arrange
        String listHeader = "List-Unsubscribe=One-Click";

        // Act
        ResponseEntity<Void> response = controller.unsubscribeOneClick("token", listHeader);

        // Assert
        verify(unsubscribeService).unsubscribe("token");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void unsubscribeGet_redirectsWhenMissingToken() {
        // Arrange
        String token = " ";

        // Act
        ResponseEntity<Void> response = controller.unsubscribeGet(token);

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/unsubscribe?error=missing_token", response.getHeaders().getLocation().toString());
    }

    @Test
    void unsubscribeGet_redirectsWhenInvalidToken() {
        // Arrange
        doThrow(new IllegalArgumentException("bad"))
                .when(unsubscribeService).unsubscribe("token");

        // Act
        ResponseEntity<Void> response = controller.unsubscribeGet("token");

        // Assert
        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("https://frontend.example/unsubscribe?error=invalid_token", response.getHeaders().getLocation().toString());
    }

    @Test
    void subscribeUserAccount_returnsBadRequestWhenMissingToken() {
        // Arrange
        String token = "";

        // Act
        ResponseEntity<Void> response = controller.subscribeUserAccount(token);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void subscribeUserAccount_returnsOkWhenSuccessful() {
        // Arrange
        String token = "token";

        // Act
        ResponseEntity<Void> response = controller.subscribeUserAccount(token);

        // Assert
        verify(subscribeService).resubscribe("token");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getSubscriptionStatus_returnsBadRequestWhenEmailMissing() {
        // Arrange
        String email = " ";

        // Act
        ResponseEntity<SubscriptionStatusResponse> response = controller.getSubscriptionStatus(email);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getSubscriptionStatus_returnsStatus() {
        // Arrange
        when(suppressionService.shouldBlock("user@example.com")).thenReturn(true);

        // Act
        ResponseEntity<SubscriptionStatusResponse> response = controller.getSubscriptionStatus("user@example.com");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.TRUE, response.getBody().getSuppressed());
    }
}
