package com.nimbly.phshoesbackend.useraccount.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.useraccount.auth.exception.InvalidCredentialsException;
import com.nimbly.phshoesbackend.useraccount.config.props.AppAuthProps;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    private final AppAuthProps authProps;
    private final Algorithm alg;

    public JwtTokenProvider(AppAuthProps props) {
        this.authProps = props;
        this.alg = Algorithm.HMAC256(props.getSecret());
    }

    public String issueAccessToken(String userId, String email) {
        var alg = Algorithm.HMAC256(authProps.getSecret());
        var now = Instant.now();
        var exp = now.plusSeconds(authProps.getAccessTtlSeconds());
        return JWT.create()
                .withIssuer(authProps.getIssuer())
                .withSubject(userId)
                .withClaim("email", email)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(exp))
                .withJWTId(UUID.randomUUID().toString())
                .sign(alg);
    }


    public DecodedJWT verify(String token) {
        return JWT.require(alg)
                .withIssuer(authProps.getIssuer())
                .build()
                .verify(token);
    }


    public DecodedJWT parseAccess(String token) {
        try {
            var alg = Algorithm.HMAC256(authProps.getSecret());
            var v = JWT.require(alg);
            if (StringUtils.hasText(authProps.getIssuer())) v = v.withIssuer(authProps.getIssuer());
            return v.build().verify(token);
        } catch (JWTVerificationException e) {
            throw new InvalidCredentialsException();
        }
    }
    public String userIdFromAuthorizationHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new com.nimbly.phshoesbackend.useraccount.auth.exception.InvalidCredentialsException();
        }
        String token = authHeader.substring(7);
        return parseAccess(token).getSubject(); // userId (sub)
    }
}
