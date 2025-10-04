package com.nimbly.phshoesbackend.useraccount.controller;

import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.exception.InvalidVerificationTokenException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationAlreadyUsedException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationExpiredException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationNotFoundException;
import com.nimbly.phshoesbackend.useraccount.model.SuppressionReason;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;
import com.nimbly.phshoesbackend.useraccount.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/verify")
public class VerificationController {

    @Autowired
    private final VerificationService verificationService;
    @Autowired
    private final SuppressionService suppressionService;
    @Autowired
    private final JwtTokenProvider jwtTokenProvider;
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;
    @Value("${app.frontend.verify-path:/}")
    private String frontendVerifyPath;

    public VerificationController(VerificationService verificationService,
                                  SuppressionService suppressionService,
                                  JwtTokenProvider jwtTokenProvider) {
        this.verificationService = verificationService;
        this.suppressionService = suppressionService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping("/")
    public ResponseEntity<Void> verify(@RequestParam("token") String token) {
        final String base = StringUtils.hasText(frontendBaseUrl) ? frontendBaseUrl : "http://localhost:5173";
        final String path = StringUtils.hasText(frontendVerifyPath) ? frontendVerifyPath : "/";

        try {
            AccountResponse resp = verificationService.verifyByToken(token);
            URI loc = UriComponentsBuilder.fromUriString(base)
                    .path(path)
                    .queryParam("verified", true)
                    .build(true)
                    .toUri();
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(loc).build();
        } catch (InvalidVerificationTokenException e) {
            return redirect(base, path, "invalid");
        } catch (VerificationNotFoundException e) {
            return redirect(base, path, "not_found");
        } catch (VerificationExpiredException e) {
            return redirect(base, path, "expired");
        } catch (VerificationAlreadyUsedException e) {
            return redirect(base, path, "used");
        } catch (Exception e) {
            return redirect(base, path, "unknown");
        }
    }

    @GetMapping("/not-me")
    public ResponseEntity<Void> notMe(@RequestParam("token") String token) {
        final String base = StringUtils.hasText(frontendBaseUrl) ? frontendBaseUrl : "http://localhost:5173";
        final String path = StringUtils.hasText(frontendVerifyPath) ? frontendVerifyPath : "/";

        try {
            // Resolve email (no verification flip)
            var resolved = verificationService.resolveEmailForToken(token); // implement below
            // Write suppression (controller orchestrates)
            suppressionService.suppress(
                    resolved.email(),
                    SuppressionReason.MANUAL,
                    "VERIFY_LINK_NOT_ME_CLICK",
                    "verificationId=" + resolved.verificationId(),
                    null // optional TTL
            );

            URI loc = UriComponentsBuilder.fromUriString(base)
                    .path(path).queryParam("not_me", true).build(true).toUri();
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(loc).build();
        } catch (InvalidVerificationTokenException e) {
            return redirectResend(base, path, "invalid");
        } catch (VerificationNotFoundException e) {
            return redirectResend(base, path, "not_found");
        } catch (Exception e) {
            return redirectResend(base, path, "unknown");
        }
    }

//    @PostMapping("/verification/resend")
//    public ResponseEntity<Void> resend(@RequestBody UserAccountsController.ResendRequest req) {
//        final String base = StringUtils.hasText(frontendBaseUrl) ? frontendBaseUrl : "http://localhost:5173";
//        final String path = StringUtils.hasText(frontendVerifyPath) ? frontendVerifyPath : "/";
//
//        try {
//            verificationService.resendVerification(req.email());
//            URI loc = UriComponentsBuilder.fromUriString(base)
//                    .path(path)
//                    .queryParam("resent", true)
//                    .queryParam("email", req.email())
//                    .build(true)
//                    .toUri();
//            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(loc).build();
//        } catch (VerificationNotFoundException e) {
//            return redirectResend(base, path, "not_found");
//        } catch (VerificationExpiredException e) {
//            return redirectResend(base, path, "expired");
//        } catch (InvalidVerificationTokenException e) {
//            return redirectResend(base, path, "invalid");
//        } catch (Exception e) {
//            return redirectResend(base, path, "unknown");
//        }
//    }

    private ResponseEntity<Void> redirect(String base, String path, String code) {
        URI loc = UriComponentsBuilder.fromUriString(base)
                .path(path)
                .queryParam("verified", false)
                .queryParam("error", code)
                .build(true)
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .headers(securityHeaders())
                .location(loc)
                .build();
    }

    private ResponseEntity<Void> redirectResend(String base, String path, String code) {
        URI loc = UriComponentsBuilder.fromUriString(base)
                .path(path)
                .queryParam("resent", false)
                .queryParam("error", code)
                .build(true)
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .headers(securityHeaders())
                .location(loc)
                .build();
    }

    private org.springframework.http.HttpHeaders securityHeaders() {
        var h = new org.springframework.http.HttpHeaders();
        h.add("Referrer-Policy", "no-referrer");
        h.add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        h.add("Pragma", "no-cache");
        // Optional hardening (safe because we only redirect):
        // h.add("X-Content-Type-Options", "nosniff");
        // h.add("X-Frame-Options", "DENY");
        return h;
    }
}
