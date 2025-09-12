package com.nimbly.phshoesbackend.useraccount.service.impl;

import com.nimbly.phshoesbackend.useraccount.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notify.provider", havingValue = "smtp")
public class SmtpNotificationServiceImpl implements NotificationService {

    private final JavaMailSender mailSender;

    @Value("${notify.email.from}")        String from;
    @Value("${notify.email.subjectPrefix:[PH-Shoes]}") String subjectPrefix;

    @Override
    public void sendEmailVerification(String to, String link, String code) {
        var msg = new org.springframework.mail.SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subjectPrefix + " Verify your account");
        msg.setText("""
                Verify your account:

                %s

                Or use code: %s (valid 15 minutes)
                """.formatted(link, code));
        mailSender.send(msg);
    }
}