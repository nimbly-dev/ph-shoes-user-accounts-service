package com.nimbly.phshoesbackend.useraccount.core.verification.impl;

import com.nimbly.phshoesbackend.notification.core.model.dto.EmailAddress;
import com.nimbly.phshoesbackend.notification.core.model.dto.EmailRequest;
import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.util.SensitiveValueMasker;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

final class VerificationEmailComposer {
    private static final String EMAIL_CATEGORY_TAG = "verification";
    private static final String EMAIL_SUBJECT = "Verify your PH Shoes account";
    private static final String TEMPLATE_PATH = "email/verification.html";

    private VerificationEmailComposer() {
    }

    static EmailRequest compose(String recipientEmail,
                                String emailHash,
                                String token,
                                NotificationEmailProps emailProps,
                                AppVerificationProps verificationProps,
                                UnsubscribeService unsubscribeService) {
        String verificationLinkBase = verificationProps.getVerificationLink();
        if (verificationLinkBase == null || verificationLinkBase.isBlank()) {
            throw new IllegalStateException("Missing required property: verification.verificationLink");
        }
        String notMeLinkBase = verificationProps.getNotMeLink();
        if (notMeLinkBase == null || notMeLinkBase.isBlank()) {
            throw new IllegalStateException("Missing required property: verification.notMeLink");
        }

        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String verificationUrl = verificationLinkBase
                + (verificationLinkBase.contains("?") ? "&" : "?")
                + "token=" + encodedToken;
        String notMeUrl = notMeLinkBase
                + (notMeLinkBase.contains("?") ? "&" : "?")
                + "token=" + encodedToken;

        String htmlTemplate;
        try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            htmlTemplate = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load template: " + TEMPLATE_PATH, e);
        }

        String renderedHtml = htmlTemplate
                .replace("{{VERIFY_URL}}", verificationUrl)
                .replace("${VERIFY_URL}", verificationUrl)
                .replace("{{NOT_ME_URL}}", notMeUrl)
                .replace("${NOT_ME_URL}", notMeUrl);

        Optional<String> listUnsubscribeHeader = unsubscribeService.buildListUnsubscribeHeader(emailHash);

        String fromHeader = emailProps.getFrom();
        String senderDisplayName = null;
        String senderAddress = fromHeader.trim();

        int openAngleIndex = fromHeader.indexOf('<');
        int closeAngleIndex = fromHeader.indexOf('>');
        if (openAngleIndex >= 0 && closeAngleIndex > openAngleIndex) {
            String displaySegment = fromHeader.substring(0, openAngleIndex).trim();
            if (displaySegment.length() >= 2) {
                char first = displaySegment.charAt(0);
                char last = displaySegment.charAt(displaySegment.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    displaySegment = displaySegment.substring(1, displaySegment.length() - 1);
                }
            }
            senderDisplayName = displaySegment.isBlank() ? null : displaySegment;
            senderAddress = fromHeader.substring(openAngleIndex + 1, closeAngleIndex).trim();
        }

        EmailRequest.EmailRequestBuilder requestBuilder = EmailRequest.builder()
                .from(EmailAddress.builder()
                        .name(senderDisplayName)
                        .address(senderAddress)
                        .build())
                .to(EmailAddress.builder().address(recipientEmail).build())
                .subject(EMAIL_SUBJECT)
                .htmlBody(renderedHtml)
                .textBody("Verify your account: " + verificationUrl)
                .tag("category", EMAIL_CATEGORY_TAG)
                .requestIdHint("verify:" + SensitiveValueMasker.hashPrefix(emailHash));

        listUnsubscribeHeader
                .filter(header -> !header.isBlank())
                .ifPresent(value -> requestBuilder.header("List-Unsubscribe", value));
        if (emailProps.getListUnsubscribePost() != null
                && !emailProps.getListUnsubscribePost().isBlank()) {
            requestBuilder.header("List-Unsubscribe-Post", emailProps.getListUnsubscribePost());
        }

        return requestBuilder.build();
    }
}

