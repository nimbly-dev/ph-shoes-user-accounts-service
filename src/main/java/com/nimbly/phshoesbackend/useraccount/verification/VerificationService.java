package com.nimbly.phshoesbackend.useraccount.verification;


public interface VerificationService {
    void resendVerification(String email);
    boolean verify(String token);
    void sendVerificationEmail(String email);
    boolean notMe(String token);
}