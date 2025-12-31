package com.nimbly.phshoesbackend.useraccount.core.verification.impl;

import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationEmailContextResolverTest {

    @Mock
    private EmailCrypto emailCrypto;
    @Mock
    private AccountRepository accountRepository;

    @Test
    void resolve_fromEmail_returnsContextWithAccount() {
        // Arrange
        Account account = new Account();
        account.setUserId("user-1");
        account.setEmailHash("hash2");

        when(emailCrypto.normalize("USER@EXAMPLE.COM")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1", "hash2"));
        when(accountRepository.findByEmailHash("hash1")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailHash("hash2")).thenReturn(Optional.of(account));

        // Act
        VerificationEmailContext context = VerificationEmailContextResolver.resolve(
                "USER@EXAMPLE.COM",
                emailCrypto,
                accountRepository
        );

        // Assert
        assertEquals("user@example.com", context.normalizedEmail());
        assertEquals(List.of("hash1", "hash2"), context.hashCandidates());
        assertEquals(Optional.of(account), context.account());
        assertEquals("hash2", context.effectiveHash());
        assertFalse(context.providedHash());
    }

    @Test
    void resolve_fromHash_usesAccountEmail() {
        // Arrange
        Account account = new Account();
        account.setUserId("user-1");
        account.setEmailHash("hash1");
        account.setEmailEnc("encrypted");

        when(accountRepository.findByEmailHash("hash1")).thenReturn(Optional.of(account));
        when(emailCrypto.decrypt("encrypted")).thenReturn("User@Example.com");
        when(emailCrypto.normalize("User@Example.com")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1"));

        // Act
        VerificationEmailContext context = VerificationEmailContextResolver.resolve(
                "hash1",
                emailCrypto,
                accountRepository
        );

        // Assert
        assertTrue(context.providedHash());
        assertEquals("user@example.com", context.normalizedEmail());
        assertEquals("hash1", context.effectiveHash());
    }

    @Test
    void resolve_fromHash_throwsWhenMissingAccount() {
        // Arrange
        when(accountRepository.findByEmailHash("hash1")).thenReturn(Optional.empty());

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                VerificationEmailContextResolver.resolve("hash1", emailCrypto, accountRepository));

        // Assert
        assertEquals("Unknown email hash", exception.getMessage());
    }
}
