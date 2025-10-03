package com.nimbly.phshoesbackend.useraccount.service.impl;

import com.nimbly.phshoesbackend.useraccount.service.NotificationService;
import com.nimbly.phshoesbackend.useraccount.service.SuppressionService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notify.provider", havingValue = "smtp")
public class SmtpNotificationServiceImpl implements NotificationService {

    private final JavaMailSender mailSender;
    private final SuppressionService suppressionService;

    @Value("${notify.email.from}")        String from;
    @Value("${notify.email.subjectPrefix:[PH-Shoes]}") String subjectPrefix;

    @Override
    public void sendEmailVerification(String to, String verifyUrl, String notMeUrl) {
        String target = to.trim().toLowerCase(Locale.ROOT);
        if (suppressionService.shouldBlock(target)) {
            log.info("smtp.skip suppressed email={}", mask(target));
            return;
        }

        String html = loadTemplate("email/verification.html")
                .replace("{{VERIFY_URL}}", verifyUrl)
                .replace("{{NOT_ME_URL}}", notMeUrl);

        try {
            MimeMessage mm = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mm, "UTF-8");
            h.setFrom("no-reply@your-domain.tld"); // TODO: configure
            h.setTo(target);
            h.setSubject("Verify your email");
            h.setText(html, true);
            mailSender.send(mm);
            log.info("smtp.sent verification to={}", mask(target));
        } catch (Exception e) {
            log.warn("smtp.send failed email={} err={}", mask(target), e.toString());
        }
    }

    private String loadTemplate(String cpPath) {
        try {
            var res = new ClassPathResource(cpPath);
            return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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