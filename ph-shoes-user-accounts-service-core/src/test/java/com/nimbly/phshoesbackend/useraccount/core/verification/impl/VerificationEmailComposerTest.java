package com.nimbly.phshoesbackend.useraccount.core.verification.impl;

import com.nimbly.phshoesbackend.notification.core.model.dto.EmailRequest;
import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationEmailComposerTest {

    @Mock
    private UnsubscribeService unsubscribeService;

    @Test
    void compose_buildsRequestWithHeadersAndLinks() {
        // Arrange
        NotificationEmailProps emailProps = new NotificationEmailProps();
        emailProps.setFrom("PH Shoes <noreply@example.com>");
        emailProps.setListUnsubscribePost("List-Unsubscribe=One-Click");

        AppVerificationProps verificationProps = new AppVerificationProps();
        verificationProps.setVerificationLink("https://example.com/verify");
        verificationProps.setNotMeLink("https://example.com/not-me");

        when(unsubscribeService.buildListUnsubscribeHeader("hash1"))
                .thenReturn(Optional.of("mailto:unsubscribe@example.com"));

        // Act
        EmailRequest request = VerificationEmailComposer.compose(
                "user@example.com",
                "hash1",
                "token-1",
                emailProps,
                verificationProps,
                unsubscribeService
        );

        // Assert
        assertEquals("noreply@example.com", request.getFrom().getAddress());
        assertEquals("PH Shoes", request.getFrom().getName());
        assertEquals(1, request.getTo().size());
        assertEquals("user@example.com", request.getTo().get(0).getAddress());
        assertEquals("Verify your PH Shoes account", request.getSubject());
        assertEquals("verification", request.getTags().get("category"));
        assertEquals("mailto:unsubscribe@example.com", request.getHeaders().get("List-Unsubscribe"));
        assertEquals("List-Unsubscribe=One-Click", request.getHeaders().get("List-Unsubscribe-Post"));
        assertTrue(request.getHtmlBody().contains("https://example.com/verify?token=token-1"));
        assertTrue(request.getHtmlBody().contains("https://example.com/not-me?token=token-1"));
        assertTrue(request.getTextBody().contains("https://example.com/verify?token=token-1"));
        assertNotNull(request.getRequestIdHint());
        assertTrue(request.getRequestIdHint().startsWith("verify:"));
    }

    @Test
    void compose_throwsWhenVerificationLinkMissing() {
        // Arrange
        NotificationEmailProps emailProps = new NotificationEmailProps();
        emailProps.setFrom("PH Shoes <noreply@example.com>");

        AppVerificationProps verificationProps = new AppVerificationProps();
        verificationProps.setVerificationLink(" ");
        verificationProps.setNotMeLink("https://example.com/not-me");

        // Act
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                VerificationEmailComposer.compose(
                        "user@example.com",
                        "hash1",
                        "token-1",
                        emailProps,
                        verificationProps,
                        unsubscribeService
                ));

        // Assert
        assertTrue(exception.getMessage().contains("verification.verificationLink"));
    }
}
