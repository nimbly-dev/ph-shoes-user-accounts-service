package com.nimbly.phshoesbackend.useraccount.web.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.useraccount.core.auth.AuthService;
import com.nimbly.phshoesbackend.useraccount.core.auth.exception.InvalidCredentialsException;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

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
        String ip = extractClientIp(req);
        String ua = req != null ? req.getHeader("User-Agent") : null;

        TokenResponse res = authService.login(
                new LoginRequest()
                        .email(normalizeEmail(loginRequest.getEmail()))
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
        DecodedJWT decoded = parseOrThrow(token);

        TokenContentResponse res = new TokenContentResponse();
        res.setSub(decoded.getSubject());
        res.setEmail(decoded.getClaim("email").asString());
        res.setIat(decoded.getIssuedAt() == null ? 0L : decoded.getIssuedAt().toInstant().getEpochSecond());
        res.setExp(decoded.getExpiresAt() == null ? 0L : decoded.getExpiresAt().toInstant().getEpochSecond());
        res.setRoles(new java.util.ArrayList<>());
        return ResponseEntity.ok(res);
    }

    @Override
    public ResponseEntity<Void> authLogout() {
        String authorizationHeader = nativeWebRequest.getHeader(HttpHeaders.AUTHORIZATION);
        authService.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String extractClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String h = request.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) {
            int comma = h.indexOf(',');
            return (comma > 0) ? h.substring(0, comma).trim() : h.trim();
        }
        h = request.getHeader("X-Real-IP");
        if (h != null && !h.isBlank()) return h.trim();
        return request.getRemoteAddr();
    }

    private DecodedJWT parseOrThrow(String token) {
        try {
            return jwtTokenService.parseAccess(token);
        } catch (JwtVerificationException ex) {
            throw new InvalidCredentialsException();
        }
    }
}
