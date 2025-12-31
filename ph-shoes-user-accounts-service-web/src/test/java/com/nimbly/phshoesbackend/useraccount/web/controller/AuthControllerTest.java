package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtVerificationException;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.useraccount.core.auth.AuthService;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccounts.model.LoginRequest;
import com.nimbly.phshoesbackend.useraccounts.model.TokenContentResponse;
import com.nimbly.phshoesbackend.useraccounts.model.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private NativeWebRequest nativeWebRequest;
    @Mock
    private HttpServletRequest httpServletRequest;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, jwtTokenService, nativeWebRequest);
    }

    @Test
    void authLogin_setsBearerAndUsesForwardedIp() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("Test@Email.com");
        request.setPassword("password");

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("token");

        when(nativeWebRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(httpServletRequest);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("agent");
        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenReturn(tokenResponse);

        // Act
        ResponseEntity<TokenResponse> response = controller.authLogin(request);

        // Assert
        assertEquals("Bearer", response.getBody().getTokenType());
        ArgumentCaptor<LoginRequest> loginCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(authService).login(loginCaptor.capture(), ipCaptor.capture(), eq("agent"));
        assertEquals("test@email.com", loginCaptor.getValue().getEmail());
        assertEquals("1.2.3.4", ipCaptor.getValue());
    }

    @Test
    void authLogin_usesRealIpWhenForwardedMissing() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("Test@Email.com");
        request.setPassword("password");

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("token");

        when(nativeWebRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(httpServletRequest);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(" ");
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn("5.6.7.8");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("agent");
        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenReturn(tokenResponse);

        // Act
        ResponseEntity<TokenResponse> response = controller.authLogin(request);

        // Assert
        ArgumentCaptor<LoginRequest> loginCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(authService).login(loginCaptor.capture(), ipCaptor.capture(), eq("agent"));
        assertEquals("test@email.com", loginCaptor.getValue().getEmail());
        assertEquals("5.6.7.8", ipCaptor.getValue());
        assertEquals("Bearer", response.getBody().getTokenType());
    }

    @Test
    void authLogin_usesRemoteAddrWhenRealIpMissing() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("Test@Email.com");
        request.setPassword("password");

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("token");

        when(nativeWebRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(httpServletRequest);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(" ");
        when(httpServletRequest.getRemoteAddr()).thenReturn("9.9.9.9");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("agent");
        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenReturn(tokenResponse);

        // Act
        ResponseEntity<TokenResponse> response = controller.authLogin(request);

        // Assert
        ArgumentCaptor<LoginRequest> loginCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(authService).login(loginCaptor.capture(), ipCaptor.capture(), eq("agent"));
        assertEquals("test@email.com", loginCaptor.getValue().getEmail());
        assertEquals("9.9.9.9", ipCaptor.getValue());
        assertEquals("Bearer", response.getBody().getTokenType());
    }

    @Test
    void authLogin_usesUnknownIpWhenRequestMissing() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("Test@Email.com");
        request.setPassword("password");

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("token");

        when(nativeWebRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(null);
        when(authService.login(any(LoginRequest.class), anyString(), isNull()))
                .thenReturn(tokenResponse);

        // Act
        controller.authLogin(request);

        // Assert
        ArgumentCaptor<LoginRequest> loginCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userAgentCaptor = ArgumentCaptor.forClass(String.class);
        verify(authService).login(loginCaptor.capture(), ipCaptor.capture(), userAgentCaptor.capture());
        assertEquals("test@email.com", loginCaptor.getValue().getEmail());
        assertEquals("unknown", ipCaptor.getValue());
        assertNull(userAgentCaptor.getValue());
    }

    @Test
    void getContentFromTokenAuth_returnsContent() {
        // Arrange
        DecodedJWT decoded = mock(DecodedJWT.class);
        Claim emailClaim = mock(Claim.class);

        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtTokenService.parseAccess("token")).thenReturn(decoded);
        when(decoded.getSubject()).thenReturn("user-1");
        when(decoded.getClaim("email")).thenReturn(emailClaim);
        when(emailClaim.asString()).thenReturn("user@example.com");
        when(decoded.getIssuedAt()).thenReturn(Date.from(Instant.ofEpochSecond(100)));
        when(decoded.getExpiresAt()).thenReturn(Date.from(Instant.ofEpochSecond(200)));

        // Act
        ResponseEntity<TokenContentResponse> response = controller.getContentFromTokenAuth();

        // Assert
        TokenContentResponse body = response.getBody();
        assertEquals("user-1", body.getSub());
        assertEquals("user@example.com", body.getEmail());
        assertEquals(Long.valueOf(100L), body.getIat());
        assertEquals(Long.valueOf(200L), body.getExp());
        assertEquals(List.of(), body.getRoles());
    }

    @Test
    void getContentFromTokenAuth_handlesMissingDates() {
        // Arrange
        DecodedJWT decoded = mock(DecodedJWT.class);
        Claim emailClaim = mock(Claim.class);

        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtTokenService.parseAccess("token")).thenReturn(decoded);
        when(decoded.getSubject()).thenReturn("user-1");
        when(decoded.getClaim("email")).thenReturn(emailClaim);
        when(emailClaim.asString()).thenReturn("user@example.com");
        when(decoded.getIssuedAt()).thenReturn(null);
        when(decoded.getExpiresAt()).thenReturn(null);

        // Act
        ResponseEntity<TokenContentResponse> response = controller.getContentFromTokenAuth();

        // Assert
        TokenContentResponse body = response.getBody();
        assertEquals(Long.valueOf(0L), body.getIat());
        assertEquals(Long.valueOf(0L), body.getExp());
    }

    @Test
    void getContentFromTokenAuth_throwsWhenMissingHeader() {
        // Arrange
        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        // Act
        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () -> controller.getContentFromTokenAuth());

        // Assert
        assertNotNull(exception);
    }

    @Test
    void getContentFromTokenAuth_throwsWhenHeaderInvalid() {
        // Arrange
        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic token");

        // Act
        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class,
                () -> controller.getContentFromTokenAuth());

        // Assert
        assertNotNull(exception);
    }

    @Test
    void getContentFromTokenAuth_throwsWhenTokenInvalid() {
        // Arrange
        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(jwtTokenService.parseAccess("token"))
                .thenThrow(new JwtVerificationException("bad", new RuntimeException("boom")));

        // Act
        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class,
                () -> controller.getContentFromTokenAuth());

        // Assert
        assertNotNull(exception);
    }

    @Test
    void authLogout_callsService() {
        // Arrange
        when(nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");

        // Act
        ResponseEntity<Void> response = controller.authLogout();

        // Assert
        verify(authService).logout("Bearer token");
        assertEquals(204, response.getStatusCodeValue());
    }
}
