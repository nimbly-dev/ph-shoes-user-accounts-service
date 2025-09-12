package com.nimbly.phshoesbackend.useraccount.service.impl;

import com.nimbly.phshoesbackend.useraccount.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notify.provider", havingValue = "ses")
public class SesNotificationServiceImpl implements NotificationService {

    @Autowired
    private SesV2Client ses;

    @Value("${notify.email.from}")        String from;
    @Value("${notify.email.subjectPrefix:[PH-Shoes]}") String subjectPrefix;

    @Override
    public void sendEmailVerification(String toEmail, String link, String code) {
        String subject = subjectPrefix + " Verify your account";
        String text = "Verify your account:\n" + link + "\n\nOr use code: " + code + " (valid 15 minutes)";
        String html = "<p>Verify your account:</p><p><a href=\"" + link + "\">" + link + "</a></p>"
                + "<p>Or use code: <b>" + code + "</b> (valid 15 minutes)</p>";

        ses.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(toEmail).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().data(subject).charset("UTF-8").build())
                                .body(Body.builder()
                                        .text(Content.builder().data(text).charset("UTF-8").build())
                                        .html(Content.builder().data(html).charset("UTF-8").build())
                                        .build())
                                .build())
                        .build())
                .build());
    }
}
