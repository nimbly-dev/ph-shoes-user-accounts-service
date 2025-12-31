package com.nimbly.phshoesbackend.useraccount.core.unsubscribe.impl;

import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.commons.core.model.SuppressionReason;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnsubscribeServiceImplTest {

    @Mock
    private UnsubscribeTokenCodec unsubscribeTokenCodec;
    @Mock
    private SuppressionService suppressionService;

    private NotificationEmailProps emailProps;
    private AppVerificationProps verificationProps;
    private UnsubscribeServiceImpl service;

    @BeforeEach
    void setUp() {
        emailProps = new NotificationEmailProps();
        emailProps.setListUnsubscribe("mailto:unsubscribe@example.com");
        emailProps.setUnsubscribeLink(null);

        verificationProps = new AppVerificationProps();
        verificationProps.setVerificationLink("https://example.com/verify/email");

        service = new UnsubscribeServiceImpl(unsubscribeTokenCodec, suppressionService, emailProps, verificationProps);
    }

    @Test
    void unsubscribe_suppressesHash() {
        // Arrange
        when(unsubscribeTokenCodec.decodeAndVerify("token")).thenReturn("hash1");

        // Act
        service.unsubscribe("token");

        // Assert
        verify(suppressionService).suppressHash(
                "hash1",
                SuppressionReason.MANUAL,
                "list-unsubscribe",
                "Manual Unsubscribe",
                null
        );
    }

    @Test
    void buildListUnsubscribeHeader_returnsEmptyWhenMissingHash() {
        // Arrange
        String emailHash = " ";

        // Act
        Optional<String> header = service.buildListUnsubscribeHeader(emailHash);

        // Assert
        assertTrue(header.isEmpty());
    }

    @Test
    void buildListUnsubscribeHeader_buildsMailtoAndOneClick() {
        // Arrange
        when(unsubscribeTokenCodec.encode("hash1")).thenReturn("token");

        // Act
        Optional<String> header = service.buildListUnsubscribeHeader("hash1");

        // Assert
        assertTrue(header.isPresent());
        assertEquals(
                "mailto:unsubscribe@example.com, <https://example.com/user-accounts/unsubscribe?token=token>",
                header.get()
        );
    }
}
