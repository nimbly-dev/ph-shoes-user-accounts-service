package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.nimbly.phshoesbackend.useraccount.core.exception.UserAccountNotificationSendException;
import com.nimbly.phshoesbackend.useraccount.core.exception.VerificationAlreadyUsedException;
import com.nimbly.phshoesbackend.useraccount.core.verification.VerificationService;
import com.nimbly.phshoesbackend.useraccount.web.util.RedirectResponses;
import com.nimbly.phshoesbackend.useraccounts.api.VerificationApi;
import com.nimbly.phshoesbackend.useraccounts.model.ResendVerificationEmailRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class VerificationController implements VerificationApi {

    private final VerificationService verificationService;

    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;

    @Value("${app.frontend.verify-path:/}")
    private String frontendVerifyPath;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    // GET /verify/email?token=...
    @Override
    public ResponseEntity<Void> verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            log.warn("Missing token");
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "missing_token",
                    null,
                    null,
                    null
            );
        }
        try {
            boolean ok = verificationService.verify(token);
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    null,
                    ok,
                    null,
                    null
            );
        } catch (VerificationAlreadyUsedException e) {
            log.warn("verification.verify already_used token={} msg={}", token, e.getMessage());
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "already_used",
                    null,
                    null,
                    null
            );
        } catch (IllegalArgumentException e) {
            log.warn("verification.verify failed msg={}", e.toString());
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "invalid_token",
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            log.error("verification.verify unexpected={}", e.toString());
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "unexpected",
                    null,
                    null,
                    null
            );
        }
    }

    // POST /verify/email/resend  body: { "email": "..." }
    @Override
    public ResponseEntity<Void> resendVerificationEmail(ResendVerificationEmailRequest body) {
        if (body == null || body.getEmail() == null || body.getEmail().isBlank()) {
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "missing_email",
                    null,
                    null,
                    null
            );
        }
        try {
            verificationService.sendVerificationEmail(body.getEmail());
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    null,
                    null,
                    true,
                    null
            );
        } catch (UserAccountNotificationSendException e) {
            log.warn("verification.resend failed email={} msg={}", body.getEmail(), e.toString());
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "send_failed",
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            log.error("verification.resend unexpected email={} ex={}", body.getEmail(), e.toString());
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "unexpected",
                    null,
                    null,
                    null
            );
        }
    }

    @Override
    public ResponseEntity<Void> markEmailNotMe(String token) {
        if (token == null || token.isBlank()) {
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "missing_params",
                    null,
                    null,
                    null
            );
        }
        try {
            boolean ok = verificationService.notMe(token);
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    null,
                    null,
                    null,
                    ok
            );
        } catch (IllegalArgumentException e) {
            log.warn("verification.notme invalid token/email msg={}", e.toString());
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "invalid_params",
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            log.error("verification.notme unexpected ex={}", e.toString());
            return RedirectResponses.seeOther(
                    frontendBaseUrl,
                    frontendVerifyPath,
                    "unexpected",
                    null,
                    null,
                    null
            );
        }
    }
}
