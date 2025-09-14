package com.nimbly.phshoesbackend.useraccount.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbly.phshoesbackend.useraccount.config.AppAuthProps;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    private final AppAuthProps props;
    private final Algorithm alg;

    public JwtTokenProvider(AppAuthProps props) {
        this.props = props;
        this.alg = Algorithm.HMAC256(props.getSecret());
    }

    public String issueAccessToken(String userId, String email) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getAccessTtlSeconds());
        return JWT.create()
                .withIssuer(props.getIssuer())
                .withSubject(userId)
                .withAudience("ph-shoes-frontend")
                .withClaim("email", email)
                .withIssuedAt(now)
                .withExpiresAt(exp)
                .withJWTId(UUID.randomUUID().toString())
                .sign(alg);
    }

    public DecodedJWT verify(String token) {
        return JWT.require(alg)
                .withIssuer(props.getIssuer())
                .build()
                .verify(token);
    }
}
