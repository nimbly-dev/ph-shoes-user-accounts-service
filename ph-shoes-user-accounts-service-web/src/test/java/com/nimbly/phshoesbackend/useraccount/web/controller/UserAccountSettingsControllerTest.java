package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtVerificationException;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.service.AccountSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountSettingsControllerTest {

    @Mock
    private AccountSettingsService accountSettingsService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private NativeWebRequest nativeWebRequest;

    private ObjectMapper objectMapper;
    private UserAccountSettingsController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new UserAccountSettingsController(
                accountSettingsService,
                jwtTokenService,
                nativeWebRequest,
                objectMapper
        );
    }

    @Test
    void getAccountSettings_returnsSettings() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", "value");

        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtTokenService.userIdFromAuthorizationHeader("Bearer token")).thenReturn("user-1");
        when(accountSettingsService.getOrInit("user-1")).thenReturn(node);

        // Act
        ResponseEntity<Object> response = controller.getAccountSettings();

        // Assert
        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        assertEquals("value", responseBody.get("key"));
    }

    @Test
    void getAccountSettings_throwsWhenInvalidToken() {
        // Arrange
        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtTokenService.userIdFromAuthorizationHeader("Bearer token"))
                .thenThrow(new JwtVerificationException("bad", null));

        // Act
        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () -> controller.getAccountSettings());

        // Assert
        assertNotNull(exception);
    }

    @Test
    void updateAccountSettings_returnsUpdatedSettings() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", "value");

        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtTokenService.userIdFromAuthorizationHeader("Bearer token")).thenReturn("user-1");
        when(accountSettingsService.update(eq("user-1"), any(JsonNode.class))).thenReturn(node);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.updateAccountSettings(node);

        // Assert
        assertEquals("value", response.getBody().get("key"));
    }

    @Test
    void updateAccountSettings_throwsWhenInvalidToken() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", "value");

        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtTokenService.userIdFromAuthorizationHeader("Bearer token"))
                .thenThrow(new JwtVerificationException("bad", null));

        // Act
        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () -> controller.updateAccountSettings(node));

        // Assert
        assertNotNull(exception);
    }
}
