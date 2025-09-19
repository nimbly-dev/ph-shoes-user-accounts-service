package com.nimbly.phshoesbackend.useraccount.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbly.phshoesbackend.useraccount.auth.AuthService;
import com.nimbly.phshoesbackend.useraccount.auth.dto.LoginRequest;
import com.nimbly.phshoesbackend.useraccount.auth.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(
            path = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest body,
                                               HttpServletRequest request) {
        String ip = clientIp(request);
        String ua = userAgent(request);
        log.info("Login attempt for email={} ip={}", safeEmail(body.getEmail()), ip);
        TokenResponse token = authService.login(body, ip, ua);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String authz = request.getHeader(HttpHeaders.AUTHORIZATION);
        authService.logout(authz);
        ResponseCookie clear = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).secure(false)
                .sameSite("Lax").path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();

        log.info("auth.logout done");
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .build();
    }

    private static String clientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        String xr = req.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) return xr;
        return req.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return ua == null ? "unknown" : ua;
    }

    private static String safeEmail(String email) {
        if (email == null || email.isBlank()) return "(blank)";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.charAt(0) + "***" + email.substring(at);
    }
}