package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.useraccount.core.auth.AuthService;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccounts.api.AuthApi;
import com.nimbly.phshoesbackend.useraccounts.model.LoginRequest;
import com.nimbly.phshoesbackend.useraccounts.model.TokenContentResponse;
import com.nimbly.phshoesbackend.useraccounts.model.TokenResponse;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtTokenService;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.ArrayList;
import java.util.Locale;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;
    private final NativeWebRequest nativeWebRequest;

    @Override
    public ResponseEntity<TokenResponse> authLogin(@Valid LoginRequest loginRequest) {
        HttpServletRequest req = nativeWebRequest.getNativeRequest(HttpServletRequest.class);
        String ip = "unknown";
        if (req != null) {
            String forwardedFor = req.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                int comma = forwardedFor.indexOf(',');
                ip = (comma > 0) ? forwardedFor.substring(0, comma).trim() : forwardedFor.trim();
            } else {
                String realIp = req.getHeader("X-Real-IP");
                ip = (realIp != null && !realIp.isBlank()) ? realIp.trim() : req.getRemoteAddr();
            }
        }
        String ua = req != null ? req.getHeader("User-Agent") : null;

        String normalizedEmail = loginRequest.getEmail() == null
                ? null
                : loginRequest.getEmail().trim().toLowerCase(Locale.ROOT);
        TokenResponse res = authService.login(
                new LoginRequest()
                        .email(normalizedEmail)
                        .password(loginRequest.getPassword()), ip, ua
        );
        if (res.getTokenType() == null) {
            res.setTokenType("Bearer");
        }
        return ResponseEntity.ok(res);
    }

    @Override
    public ResponseEntity<TokenContentResponse> getContentFromTokenAuth() {
        String authorizationHeader = nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException();
        }
        String token = authorizationHeader.substring(7).trim();
        DecodedJWT decoded;
        try {
            decoded = jwtTokenService.parseAccess(token);
        } catch (JwtVerificationException ex) {
            throw new InvalidCredentialsException();
        }

        TokenContentResponse res = new TokenContentResponse();
        res.setSub(decoded.getSubject());
        res.setEmail(decoded.getClaim("email").asString());
        res.setIat(decoded.getIssuedAt() == null ? 0L : decoded.getIssuedAt().toInstant().getEpochSecond());
        res.setExp(decoded.getExpiresAt() == null ? 0L : decoded.getExpiresAt().toInstant().getEpochSecond());
        res.setRoles(new ArrayList<>());
        return ResponseEntity.ok(res);
    }

    @Override
    public ResponseEntity<Void> authLogout() {
        String authorizationHeader = nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION);
        authService.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }

}
