package com.nimbly.phshoesbackend.useraccount.core.unsubscribe.impl;

import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidVerificationTokenException;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeTokenCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes unsubscribe tokens as HMAC-signed payloads built from the email hash.
 * Format: {@code UNSUB:<emailHash>.<signature>} using the verification secret.
 */
@Component
@RequiredArgsConstructor
public class HmacUnsubscribeTokenCodec implements UnsubscribeTokenCodec {

    private static final String PREFIX = "UNSUB:";

    private final AppVerificationProps verificationProps;

    @Override
    public String encode(String emailHash) {
        String payload = PREFIX + emailHash;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(payloadBytes);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        return encodedPayload + "." + encodedSignature;
    }

    @Override
    public String decodeAndVerify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length == 2) {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            verifySignature(signature, payloadBytes);

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            if (!payload.startsWith(PREFIX)) {
                throw new InvalidVerificationTokenException("Invalid token type");
            }
            return payload.substring(PREFIX.length());
        }
        throw new InvalidVerificationTokenException("Invalid token format");
    }

    private byte[] sign(byte[] payloadBytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(verificationProps.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payloadBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign unsubscribe token", e);
        }
    }

    private void verifySignature(byte[] signature, byte[] payloadBytes) {
        byte[] expected = sign(payloadBytes);
        if (signature == null || expected == null || signature.length != expected.length) {
            throw new InvalidVerificationTokenException("Invalid token signature");
        }
        int result = 0;
        for (int i = 0; i < signature.length; i++) {
            result |= signature[i] ^ expected[i];
        }
        if (result != 0) {
            throw new InvalidVerificationTokenException("Invalid token signature");
        }
    }
}
