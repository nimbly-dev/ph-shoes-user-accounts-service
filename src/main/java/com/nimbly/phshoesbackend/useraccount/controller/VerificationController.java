package com.nimbly.phshoesbackend.useraccount.controller;

import com.nimbly.phshoesbackend.useraccounts.api.VerificationApi;
import com.nimbly.phshoesbackend.useraccounts.model.ResendVerificationEmailRequest;
import com.nimbly.phshoesbackend.useraccount.exception.NotificationSendException;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

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
            return redirect(frontendBaseUrl, frontendVerifyPath, "missing_token", null, null, null);
        }
        try {
            boolean ok = verificationService.verify(token);
            return redirect(frontendBaseUrl, frontendVerifyPath, null, ok, null, null);
        } catch (IllegalArgumentException e) {
            log.warn("verification.verify failed msg={}", e.toString());
            return redirect(frontendBaseUrl, frontendVerifyPath, "invalid_token", null, null, null);
        } catch (Exception e) {
            log.error("verification.verify unexpected={}", e.toString());
            return redirect(frontendBaseUrl, frontendVerifyPath, "unexpected", null, null, null);
        }
    }

    // POST /verify/email/resend  body: { "email": "..." }
    @Override
    public ResponseEntity<Void> resendVerificationEmail(ResendVerificationEmailRequest body) {
        if (body == null || body.getEmail() == null || body.getEmail().isBlank()) {
            return redirect(frontendBaseUrl, frontendVerifyPath, "missing_email", null, null, null);
        }
        try {
            verificationService.sendVerificationEmail(body.getEmail());
            return redirect(frontendBaseUrl, frontendVerifyPath, null, null, true, null);
        } catch (NotificationSendException e) {
            log.warn("verification.resend failed email={} msg={}", body.getEmail(), e.toString());
            return redirect(frontendBaseUrl, frontendVerifyPath, "send_failed", null, null, null);
        } catch (Exception e) {
            log.error("verification.resend unexpected email={} ex={}", body.getEmail(), e.toString());
            return redirect(frontendBaseUrl, frontendVerifyPath, "unexpected", null, null, null);
        }
    }

    @Override
    public ResponseEntity<Void> markEmailNotMe(String token) {
        if ((token == null || token.isBlank())) {
            return redirect(frontendBaseUrl, frontendVerifyPath, "missing_params", null, null, null);
        }
        try {
            boolean ok = verificationService.notMe(token);
            return redirect(frontendBaseUrl, frontendVerifyPath, null, null, null, ok);
        } catch (IllegalArgumentException e) {
            log.warn("verification.notme invalid token/email msg={}", e.toString());
            return redirect(frontendBaseUrl, frontendVerifyPath, "invalid_params", null, null, null);
        } catch (Exception e) {
            log.error("verification.notme unexpected ex={}", e.toString());
            return redirect(frontendBaseUrl, frontendVerifyPath, "unexpected", null, null, null);
        }
    }

    // Helper: builds 303 redirect with optional flags
    private ResponseEntity<Void> redirect(
            String base, String path, String errorCode,
            Boolean verified, Boolean resent, Boolean notMe
    ) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(base).path(path);
        if (errorCode != null) b.queryParam("error", errorCode);
        if (verified != null) b.queryParam("verified", verified);
        if (resent != null) b.queryParam("resent", resent);
        if (notMe != null) b.queryParam("notMe", notMe);
        URI location = b.build(true).toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
}
