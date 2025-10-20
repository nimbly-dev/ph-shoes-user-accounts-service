package com.nimbly.phshoesbackend.useraccount.verification;

import com.nimbly.phshoesbackend.useraccount.model.ResolvedEmail;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;

public interface VerificationService {
    void sendVerificationEmail(String recipientEmail);
    void resend(String emailRaw);
    AccountResponse verifyByToken(String token);
    ResolvedEmail resolveEmailForToken(String token);
}
