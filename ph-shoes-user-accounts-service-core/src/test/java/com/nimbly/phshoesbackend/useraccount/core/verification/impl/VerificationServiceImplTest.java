package com.nimbly.phshoesbackend.useraccount.core.verification.impl;

import com.nimbly.phshoesbackend.commons.core.model.SuppressionReason;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.notification.core.model.dto.EmailRequest;
import com.nimbly.phshoesbackend.notification.core.model.dto.SendResult;
import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.notification.core.service.NotificationService;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.exception.VerificationExpiredException;
import com.nimbly.phshoesbackend.useraccount.core.model.Account;
import com.nimbly.phshoesbackend.useraccount.core.model.VerificationEntry;
import com.nimbly.phshoesbackend.useraccount.core.model.VerificationStatus;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.repository.VerificationRepository;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.verification.VerificationTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceImplTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private VerificationTokenCodec tokenCodec;
    @Mock
    private SuppressionService suppressionService;
    @Mock
    private VerificationRepository verificationRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private EmailCrypto emailCrypto;
    @Mock
    private UnsubscribeService unsubscribeService;

    private NotificationEmailProps emailProps;
    private AppVerificationProps verificationProps;
    private VerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        emailProps = new NotificationEmailProps();
        emailProps.setFrom("PH Shoes <noreply@example.com>");
        emailProps.setListUnsubscribePost("");

        verificationProps = new AppVerificationProps();
        verificationProps.setSecret("secret");
        verificationProps.setTtlSeconds(120);
        verificationProps.setVerificationLink("https://example.com/verify/email");
        verificationProps.setNotMeLink("https://example.com/verify/not-me");

        service = new VerificationServiceImpl(
                notificationService,
                emailProps,
                tokenCodec,
                suppressionService,
                verificationRepository,
                accountRepository,
                verificationProps,
                emailCrypto,
                unsubscribeService
        );
    }

    @Test
    void sendVerificationEmail_skipsWhenSuppressed() {
        // Arrange
        when(emailCrypto.normalize("user@example.com")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1"));
        when(accountRepository.findByEmailHash("hash1")).thenReturn(Optional.empty());
        when(suppressionService.shouldBlock("user@example.com")).thenReturn(true);

        // Act
        service.sendVerificationEmail("user@example.com");

        // Assert
        verify(verificationRepository, never()).put(any());
        verify(notificationService, never()).sendEmailVerification(any());
    }

    @Test
    void sendVerificationEmail_sendsMessageWhenAllowed() {
        // Arrange
        when(emailCrypto.normalize("user@example.com")).thenReturn("user@example.com");
        when(emailCrypto.hashCandidates("user@example.com")).thenReturn(List.of("hash1"));
        when(accountRepository.findByEmailHash("hash1")).thenReturn(Optional.empty());
        when(suppressionService.shouldBlock("user@example.com")).thenReturn(false);
        when(tokenCodec.encode(anyString())).thenReturn("token");
        when(unsubscribeService.buildListUnsubscribeHeader("hash1")).thenReturn(Optional.empty());
        when(notificationService.sendEmailVerification(any(EmailRequest.class)))
                .thenReturn(SendResult.builder()
                        .provider("smtp")
                        .messageId("message-1")
                        .acceptedAt(Instant.now())
                        .requestId("req-1")
                        .build());

        // Act
        service.sendVerificationEmail("user@example.com");

        // Assert
        ArgumentCaptor<VerificationEntry> entryCaptor = ArgumentCaptor.forClass(VerificationEntry.class);
        verify(verificationRepository).put(entryCaptor.capture());
        VerificationEntry entry = entryCaptor.getValue();
        assertEquals(VerificationStatus.PENDING, entry.getStatus());
        assertEquals("hash1", entry.getEmailHash());
        verify(notificationService).sendEmailVerification(any(EmailRequest.class));
    }

    @Test
    void verify_marksAccountVerified() {
        // Arrange
        VerificationEntry entry = new VerificationEntry();
        entry.setVerificationId("verify-1");
        entry.setUserId("user-1");
        entry.setEmailHash("hash1");
        entry.setStatus(VerificationStatus.PENDING);
        entry.setExpiresAt(Instant.now().getEpochSecond() + 600);

        Account account = new Account();
        account.setUserId("user-1");
        account.setIsVerified(false);

        when(tokenCodec.decodeAndVerify("token")).thenReturn("verify-1");
        when(verificationRepository.getById("verify-1", true)).thenReturn(Optional.of(entry));
        when(accountRepository.findByUserId("user-1")).thenReturn(Optional.of(account));

        // Act
        boolean result = service.verify("token");

        // Assert
        assertTrue(result);
        verify(verificationRepository).markUsedIfPendingAndNotExpired(eq("verify-1"), anyLong());
        verify(accountRepository).setVerified("user-1", true);
    }

    @Test
    void verify_throwsWhenExpired() {
        // Arrange
        VerificationEntry entry = new VerificationEntry();
        entry.setVerificationId("verify-1");
        entry.setStatus(VerificationStatus.PENDING);
        entry.setExpiresAt(Instant.now().getEpochSecond() - 10);

        when(tokenCodec.decodeAndVerify("token")).thenReturn("verify-1");
        when(verificationRepository.getById("verify-1", true)).thenReturn(Optional.of(entry));

        // Act
        VerificationExpiredException exception = assertThrows(VerificationExpiredException.class, () -> service.verify("token"));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void notMe_suppressesHash() {
        // Arrange
        VerificationEntry entry = new VerificationEntry();
        entry.setVerificationId("verify-1");
        entry.setStatus(VerificationStatus.PENDING);
        entry.setEmailHash("hash1");

        when(tokenCodec.decodeAndVerify("token")).thenReturn("verify-1");
        when(verificationRepository.getById("verify-1", true)).thenReturn(Optional.of(entry));

        // Act
        boolean result = service.notMe("token");

        // Assert
        assertTrue(result);
        verify(suppressionService).suppressHash(
                "hash1",
                SuppressionReason.COMPLAINT,
                "verification",
                "User clicked 'Not me'",
                null
        );
    }
}
