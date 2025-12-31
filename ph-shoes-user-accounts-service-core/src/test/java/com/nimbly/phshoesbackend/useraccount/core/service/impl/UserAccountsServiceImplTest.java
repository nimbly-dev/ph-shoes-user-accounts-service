package com.nimbly.phshoesbackend.useraccount.core.service.impl;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountRequest;
import com.nimbly.phshoesbackend.useraccounts.model.CreateUserAccountResponse;
import com.nimbly.phshoesbackend.useraccounts.model.TokenContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountsServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private EmailCrypto emailCrypto;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserAccountsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAccountsServiceImpl(
                accountRepository,
                emailCrypto,
                jwtTokenService,
                passwordEncoder
        );
    }

    @Test
    void register_createsAccount_whenEmailUnique() {
        // Arrange
        CreateUserAccountRequest request = new CreateUserAccountRequest();
        request.setEmail("Test@Email.com");
        request.setPassword("password");

        when(emailCrypto.normalize("Test@Email.com")).thenReturn("test@email.com");
        when(emailCrypto.hashCandidates("test@email.com")).thenReturn(List.of("hash1"));
        when(emailCrypto.encrypt("test@email.com")).thenReturn("encrypted");
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(accountRepository.existsByEmailHash("hash1")).thenReturn(false);

        // Act
        CreateUserAccountResponse response = service.register(request);

        // Assert
        assertNotNull(response.getUserid());
        assertEquals("hash1", response.getEmail());
        assertEquals(Boolean.FALSE, response.getEmailVerified());

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account saved = accountCaptor.getValue();
        assertEquals("hash1", saved.getEmailHash());
        assertEquals("encrypted", saved.getEmailEnc());
        assertEquals("encoded", saved.getPasswordHash());
        assertEquals(Boolean.FALSE, saved.getIsVerified());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void register_throwsWhenDuplicateEmail() {
        // Arrange
        CreateUserAccountRequest request = new CreateUserAccountRequest();
        request.setEmail("dup@example.com");
        request.setPassword("password");

        when(emailCrypto.normalize("dup@example.com")).thenReturn("dup@example.com");
        when(emailCrypto.hashCandidates("dup@example.com")).thenReturn(List.of("hash1"));
        when(accountRepository.existsByEmailHash("hash1")).thenReturn(true);

        // Act
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.register(request));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void getContentFromToken_throwsWhenHeaderMissing() {
        // Arrange
        String authorizationHeader = null;

        // Act
        InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () -> service.getContentFromToken(authorizationHeader));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void getContentFromToken_returnsTokenContent() {
        // Arrange
        String token = "token";
        DecodedJWT decoded = mock(DecodedJWT.class);
        Claim emailClaim = mock(Claim.class);
        Claim rolesClaim = mock(Claim.class);

        when(jwtTokenService.parseAccess(token)).thenReturn(decoded);
        when(decoded.getSubject()).thenReturn("user-1");
        when(decoded.getClaim("email")).thenReturn(emailClaim);
        when(emailClaim.asString()).thenReturn("user@example.com");
        when(decoded.getIssuedAt()).thenReturn(Date.from(Instant.ofEpochSecond(100)));
        when(decoded.getExpiresAt()).thenReturn(Date.from(Instant.ofEpochSecond(200)));
        when(decoded.getClaim("roles")).thenReturn(rolesClaim);
        when(rolesClaim.isNull()).thenReturn(false);
        when(rolesClaim.asList(String.class)).thenReturn(List.of("USER"));

        // Act
        TokenContentResponse response = service.getContentFromToken("Bearer " + token);

        // Assert
        assertEquals("user-1", response.getSub());
        assertEquals("user@example.com", response.getEmail());
        assertEquals(List.of("USER"), response.getRoles());
        assertEquals(Long.valueOf(100L), response.getIat());
        assertEquals(Long.valueOf(200L), response.getExp());
    }
}
