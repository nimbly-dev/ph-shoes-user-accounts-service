package com.nimbly.phshoesbackend.useraccount.core.unsubscribe.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppAuthProps;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidVerificationTokenException;
import com.nimbly.phshoesbackend.services.common.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeTokenCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Encodes unsubscribe tokens as HMAC-signed payloads built from the email hash.
 * Format: {@code UNSUB:<emailHash>.<signature>} using the verification secret.
 * Also supports decoding legacy JWT-formatted tokens for backward compatibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HmacUnsubscribeTokenCodec implements UnsubscribeTokenCodec {

    private static final String PREFIX = "UNSUB:";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> HASH_FIELD_CANDIDATES = List.of(
            "emailHash", "hash", "email_hash", "sub"
    );
    private static final List<String> EMAIL_FIELD_CANDIDATES = List.of(
            "email", "address", "emailAddress"
    );

    private final AppVerificationProps verificationProps;
    private final AppAuthProps appAuthProps;
    private final EmailCrypto emailCrypto;

    @Override
    public String encode(String emailHash) {
        String payload = PREFIX + emailHash;
        byte[] signature = hmacWithVerificationSecret(payload.getBytes(StandardCharsets.UTF_8));
        return encodeUrlSafe(payload.getBytes(StandardCharsets.UTF_8)) + "." + encodeUrlSafe(signature);
    }

    @Override
    public String decodeAndVerify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length == 2) {
            return decodeCurrentFormat(parts);
        }
        if (parts.length == 3) {
            return decodeLegacyJwt(parts);
        }
        throw new InvalidVerificationTokenException("Invalid token format");
    }

    private String decodeCurrentFormat(String[] parts) {
        byte[] payloadBytes = decodeUrlSafe(parts[0]);
        byte[] signature = decodeUrlSafe(parts[1]);
        byte[] expected = hmacWithVerificationSecret(payloadBytes);

        if (!constantTimeEquals(signature, expected)) {
            throw new InvalidVerificationTokenException("Invalid token signature");
        }

        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        if (!payload.startsWith(PREFIX)) {
            throw new InvalidVerificationTokenException("Invalid token type");
        }
        return payload.substring(PREFIX.length());
    }

    private String decodeLegacyJwt(String[] parts) {
        byte[] headerPayload = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
        byte[] signature = decodeUrlSafe(parts[2]);
        if (!matchesAnySecret(headerPayload, signature)) {
            throw new InvalidVerificationTokenException("Invalid token signature");
        }

        try {
            JsonNode payload = MAPPER.readTree(decodeUrlSafe(parts[1]));

            for (String candidate : HASH_FIELD_CANDIDATES) {
                JsonNode node = payload.get(candidate);
                if (node != null && !node.asText().isBlank()) {
                    return node.asText();
                }
            }

            for (String candidate : EMAIL_FIELD_CANDIDATES) {
                JsonNode node = payload.get(candidate);
                if (node != null && !node.asText().isBlank()) {
                    String normalized = emailCrypto.normalize(node.asText());
                    if (normalized == null || normalized.isBlank()) {
                        continue;
                    }
                    return emailCrypto.hash(normalized);
                }
            }

            throw new InvalidVerificationTokenException("Legacy token missing email hash");
        } catch (InvalidVerificationTokenException e) {
            throw e;
        } catch (Exception e) {
            log.warn("unsubscribe.legacy_decode_failed msg={}", e.getMessage());
            throw new InvalidVerificationTokenException("Legacy token decode failure");
        }
    }

    private boolean matchesAnySecret(byte[] payload, byte[] signature) {
        for (String secret : candidateSecrets()) {
            if (secret == null || secret.isBlank()) {
                continue;
            }
            byte[] expected = hmac(payload, secret);
            if (constantTimeEquals(signature, expected)) {
                return true;
            }
        }
        return false;
    }

    private List<String> candidateSecrets() {
        List<String> secrets = new ArrayList<>();
        secrets.add(verificationProps.getSecret());
        if (appAuthProps.getSecret() != null && !appAuthProps.getSecret().isBlank()) {
            secrets.add(appAuthProps.getSecret());
        }
        return secrets;
    }

    private byte[] hmacWithVerificationSecret(byte[] payload) {
        return hmac(payload, verificationProps.getSecret());
    }

    private byte[] hmac(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign unsubscribe token", e);
        }
    }

    private static String encodeUrlSafe(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decodeUrlSafe(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
