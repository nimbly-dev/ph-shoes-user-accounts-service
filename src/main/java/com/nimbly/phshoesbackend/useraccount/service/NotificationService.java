package com.nimbly.phshoesbackend.useraccount.service;

public interface NotificationService {
    void sendEmailVerification(String toEmail, String link, String code);
}
