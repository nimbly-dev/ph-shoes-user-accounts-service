package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtVerificationException;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.exception.AccountBlockedException;
import com.nimbly.phshoesbackend.useraccount.core.exception.UserAccountNotificationSendException;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.service.UserAccountsService;
import com.nimbly.phshoesbackend.useraccount.core.verification.VerificationService;
import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountRequest;
import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountResponse;
import com.nimbly.phshoesbackend.useraccounts.model.TokenContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountsControllerTest {

    @Mock
    private UserAccountsService accountService;
    @Mock
    private VerificationService verificationService;
    @Mock
    private SuppressionService suppressionService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private NativeWebRequest nativeWebRequest;

    private UserAccountsController controller;

    @BeforeEach
    void setUp() {
        controller = new UserAccountsController(
                accountService,
                verificationService,
                suppressionService,
                jwtTokenService,
                nativeWebRequest
        );
    }

    @Test
    void createUserAccount_returnsCreatedWhenAllowed() {
        // Arrange
        CreateUserAccountRequest request = new CreateUserAccountRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");

        CreateUserAccountResponse response = new CreateUserAccountResponse();
        response.setUserid("user-1");
        response.setEmail("hash1");
        response.setEmailVerified(Boolean.FALSE);

        when(suppressionService.shouldBlock("user@example.com")).thenReturn(false);
        when(accountService.register(request)).thenReturn(response);

        // Act
        ResponseEntity<CreateUserAccountResponse> result = controller.createUserAccount(request);

        // Assert
        assertEquals(201, result.getStatusCodeValue());
        assertEquals("user-1", result.getBody().getUserid());
    }

    @Test
    void createUserAccount_throwsWhenSuppressed() {
        // Arrange
        CreateUserAccountRequest request = new CreateUserAccountRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");

        when(suppressionService.shouldBlock("user@example.com")).thenReturn(true);

        // Act
        AccountBlockedException exception = assertThrows(AccountBlockedException.class, () -> controller.createUserAccount(request));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void createUserAccount_rollsBackWhenVerificationFails() {
        // Arrange
        CreateUserAccountRequest request = new CreateUserAccountRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");

        CreateUserAccountResponse response = new CreateUserAccountResponse();
        response.setUserid("user-1");
        response.setEmail("hash1");
        response.setEmailVerified(Boolean.FALSE);

        when(suppressionService.shouldBlock("user@example.com")).thenReturn(false);
        when(accountService.register(request)).thenReturn(response);
        doThrow(new RuntimeException("boom"))
                .when(verificationService).sendVerificationEmail("user@example.com");

        // Act
        UserAccountNotificationSendException exception = assertThrows(UserAccountNotificationSendException.class, () -> controller.createUserAccount(request));

        // Assert
        assertNotNull(exception);
        verify(accountService).deleteOwnAccount("user-1");
    }

    @Test
    void getTokenContent_returnsResponse() {
        // Arrange
        TokenContentResponse tokenContentResponse = new TokenContentResponse();
        tokenContentResponse.setSub("user-1");
        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(accountService.getContentFromToken("Bearer token")).thenReturn(tokenContentResponse);

        // Act
        ResponseEntity<TokenContentResponse> response = controller.getTokenContent();

        // Assert
        assertEquals("user-1", response.getBody().getSub());
    }

    @Test
    void deleteMyAccount_throwsWhenTokenInvalid() {
        // Arrange
        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtTokenService.userIdFromAuthorizationHeader("Bearer token"))
                .thenThrow(new JwtVerificationException("bad", null));

        // Act
        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () -> controller.deleteMyAccount());

        // Assert
        assertNotNull(exception);
    }

    @Test
    void deleteMyAccount_deletesWhenValid() {
        // Arrange
        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtTokenService.userIdFromAuthorizationHeader("Bearer token")).thenReturn("user-1");

        // Act
        ResponseEntity<Void> response = controller.deleteMyAccount();

        // Assert
        verify(accountService).deleteOwnAccount("user-1");
        assertEquals(204, response.getStatusCodeValue());
    }
}
