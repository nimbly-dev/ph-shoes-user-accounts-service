package com.nimbly.phshoesbackend.useraccount.core.auth.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.useraccount.core.exception.AccountLockedException;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.config.props.LockoutProps;
import com.nimbly.phshoesbackend.useraccount.core.exception.EmailNotVerifiedException;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.repository.SessionRepository;
import com.nimbly.phshoesbackend.useraccount.core.repository.VerificationRepository;
import com.nimbly.phshoesbackend.useraccounts.model.LoginRequest;
import com.nimbly.phshoesbackend.useraccounts.model.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AccountRepository accounts;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private EmailCrypto emailCrypto;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private VerificationRepository verificationRepository;

    private LockoutProps lockoutProps;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        lockoutProps = new LockoutProps();
        service = new AuthServiceImpl(
                accounts,
                passwordEncoder,
                jwtTokenService,
                lockoutProps,
                emailCrypto,
                sessionRepository,
                verificationRepository
        );
    }

    @Test
    void login_throwsInvalidCredentials_whenAccountMissing() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");

        when(emailCrypto.normalize("user@example.com")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1"));
        when(accounts.findByAnyEmailHash(List.of("hash1"))).thenReturn(Optional.empty());

        // Act
        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () -> service.login(request, "127.0.0.1", "ua"));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void login_throwsAccountLocked_whenLocked() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");

        Account account = new Account();
        account.setUserId("user-1");
        account.setEmailHash("hash1");
        account.setPasswordHash("encoded");
        account.setIsVerified(true);
        account.setLockUntil(Instant.now().plusSeconds(60));

        when(emailCrypto.normalize("user@example.com")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1"));
        when(accounts.findByAnyEmailHash(List.of("hash1"))).thenReturn(Optional.of(account));
        // Act
        AccountLockedException exception = assertThrows(AccountLockedException.class, () -> service.login(request, "127.0.0.1", "ua"));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void login_returnsToken_whenCredentialsValid() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");

        Account account = new Account();
        account.setUserId("user-1");
        account.setEmailHash("hash1");
        account.setPasswordHash("encoded");
        account.setIsVerified(true);

        DecodedJWT decoded = mock(DecodedJWT.class);
        when(decoded.getId()).thenReturn("jti-1");
        when(decoded.getExpiresAt()).thenReturn(Date.from(Instant.now().plusSeconds(600)));

        when(emailCrypto.normalize("user@example.com")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1"));
        when(accounts.findByAnyEmailHash(List.of("hash1"))).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(jwtTokenService.issueAccessToken("user-1", "user@example.com")).thenReturn("token");
        when(jwtTokenService.parseAccess("token")).thenReturn(decoded);
        when(jwtTokenService.getAccessTtlSeconds()).thenReturn(3600L);

        // Act
        TokenResponse response = service.login(request, "127.0.0.1", "ua");

        // Assert
        assertEquals("token", response.getAccessToken());
        assertEquals(Long.valueOf(3600L), response.getExpiresIn());
        verify(accounts).save(any(Account.class));
        verify(sessionRepository).createSession(anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void login_throwsEmailNotVerified_whenNotVerified() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");

        Account account = new Account();
        account.setUserId("user-1");
        account.setEmailHash("hash1");
        account.setPasswordHash("encoded");
        account.setIsVerified(false);

        when(emailCrypto.normalize("user@example.com")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1"));
        when(accounts.findByAnyEmailHash(List.of("hash1"))).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(verificationRepository.hasVerifiedEntryForEmailHash("hash1")).thenReturn(false);

        // Act
        EmailNotVerifiedException exception = assertThrows(EmailNotVerifiedException.class, () -> service.login(request, "127.0.0.1", "ua"));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void logout_throwsInvalidCredentials_whenHeaderMissing() {
        // Arrange
        String authorizationHeader = null;

        // Act
        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () -> service.logout(authorizationHeader));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void logout_revokesSession_whenValid() {
        // Arrange
        DecodedJWT decoded = mock(DecodedJWT.class);
        when(jwtTokenService.parseAccess("token")).thenReturn(decoded);
        when(decoded.getId()).thenReturn("jti-1");
        when(decoded.getSubject()).thenReturn("user-1");
        when(sessionRepository.isSessionActive("jti-1")).thenReturn(true);

        // Act
        service.logout("Bearer token");

        // Assert
        verify(sessionRepository).revokeSession("jti-1");
    }
}

