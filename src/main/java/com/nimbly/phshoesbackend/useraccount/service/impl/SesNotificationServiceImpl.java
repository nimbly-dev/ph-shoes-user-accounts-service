package com.nimbly.phshoesbackend.useraccount.service.impl;

import com.nimbly.phshoesbackend.useraccount.service.NotificationService;
import com.nimbly.phshoesbackend.useraccount.service.SuppressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notify.provider", havingValue = "ses")
public class SesNotificationServiceImpl implements NotificationService {

    @Autowired
    private SesV2Client ses;
    private final SuppressionService suppressionService;

    @Value("${notify.email.from}")
    String from;
    @Value("${notify.email.subjectPrefix:[PH-Shoes]}")
    String subjectPrefix;

    @Override
    public void sendEmailVerification(String to, String verifyUrl, String notMeUrl) {
        String target = to.trim().toLowerCase(Locale.ROOT);
        if (suppressionService.shouldBlock(target)) {
            log.info("ses.skip suppressed email={}", mask(target));
            return;
        }

        String html = loadTemplate("email/verification.html");
        html = html.replace("{{VERIFY_URL}}", verifyUrl)
                .replace("{{NOT_ME_URL}}", notMeUrl);

        SendEmailRequest req = SendEmailRequest.builder()
                .fromEmailAddress("no-reply@your-domain.tld") // TODO: set verified identity
                .destination(Destination.builder().toAddresses(target).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().data("Verify your email").build())
                                .body(Body.builder().html(Content.builder().data(html).build()).build())
                                .build())
                        .build())
                .build();

        ses.sendEmail(req);
        log.info("ses.sent verification to={}", mask(target));
    }

    private String loadTemplate(String cpPath) {
        try {
            ClassPathResource res = new ClassPathResource(cpPath);
            byte[] bytes = res.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + cpPath, e);
        }
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
