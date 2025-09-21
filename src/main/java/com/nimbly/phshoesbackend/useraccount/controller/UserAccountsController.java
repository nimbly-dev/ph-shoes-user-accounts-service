package com.nimbly.phshoesbackend.useraccount.controller;

import com.nimbly.phshoesbackend.useraccount.auth.JwtTokenProvider;
import com.nimbly.phshoesbackend.useraccount.auth.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.exception.InvalidVerificationTokenException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationAlreadyUsedException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationExpiredException;
import com.nimbly.phshoesbackend.useraccount.exception.VerificationNotFoundException;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountCreateRequest;
import com.nimbly.phshoesbackend.useraccount.model.dto.AccountResponse;
import com.nimbly.phshoesbackend.useraccount.model.dto.GetContentFromTokenResponse;
import com.nimbly.phshoesbackend.useraccount.service.UserAccountsService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/user-accounts")
public class UserAccountsController {

    @Autowired
    private final UserAccountsService accountService;
    @Autowired
    private final JwtTokenProvider jwtTokenProvider;
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;
    @Value("${app.frontend.verify-path:/}")
    private String frontendVerifyPath;

    public UserAccountsController(UserAccountsService accountService,
                                  JwtTokenProvider jwtTokenProvider) {
        this.accountService = accountService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE) // ← no path here
    public ResponseEntity<GetContentFromTokenResponse> getEmailFromToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authz) {
        var dto = accountService.getContentFromTokenBearer(authz);
        return ResponseEntity.ok(dto); // 200 is correct for GET
    }
    
    @PostMapping(value = "/register",
            consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody AccountCreateRequest body) {
        var created = accountService.register(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam("token") String token) {
        final String base = StringUtils.hasText(frontendBaseUrl) ? frontendBaseUrl : "http://localhost:5173";
        final String path = StringUtils.hasText(frontendVerifyPath) ? frontendVerifyPath : "/";

        try {
            AccountResponse resp = accountService.verifyByToken(token);
            URI loc = UriComponentsBuilder.fromUriString(base)
                    .path(path)
                    .queryParam("verified", true)
                    .queryParam("email", resp.getEmail())
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

    @PostMapping("/verification/resend")
    public ResponseEntity<Void> resend(@RequestBody ResendRequest req) {
        final String base = StringUtils.hasText(frontendBaseUrl) ? frontendBaseUrl : "http://localhost:5173";
        final String path = StringUtils.hasText(frontendVerifyPath) ? frontendVerifyPath : "/";

        try {
            accountService.resendVerification(req.email());
            URI loc = UriComponentsBuilder.fromUriString(base)
                    .path(path)
                    .queryParam("resent", true)
                    .queryParam("email", req.email())
                    .build(true)
                    .toUri();
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(loc).build();
        } catch (VerificationNotFoundException e) {
            return redirectResend(base, path, "not_found");
        } catch (VerificationExpiredException e) {
            return redirectResend(base, path, "expired");
        } catch (InvalidVerificationTokenException e) {
            return redirectResend(base, path, "invalid");
        } catch (Exception e) {
            return redirectResend(base, path, "unknown");
        }
    }


    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteMyAccount(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        String userId = jwtTokenProvider.userIdFromAuthorizationHeader(authorization);
        accountService.deleteOwnAccount(userId);
        log.info("user.delete completed userId={}", userId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> redirect(String base, String path, String code) {
        URI loc = UriComponentsBuilder.fromUriString(base)
                .path(path)
                .queryParam("verified", false)
                .queryParam("error", code)
                .build(true)
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(loc).build();
    }

    private ResponseEntity<Void> redirectResend(String base, String path, String code) {
        URI loc = UriComponentsBuilder.fromUriString(base)
                .path(path)
                .queryParam("resent", false)
                .queryParam("error", code)
                .build(true)
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(loc).build();
    }

    public record ResendRequest(String email) {}
}
