package com.nimbly.phshoesbackend.useraccount.security;

import com.nimbly.phshoesbackend.services.common.core.utility.EmailCryptoUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.email")
public class EmailCrypto {
    @Getter
    private String hmacPepperB64;
    @Getter
    private String aesKeyB64;
    private List<String> legacyPeppersB64 = new ArrayList<>();

    @PostConstruct
    void validateKeys() {
        byte[] key = decodeMandatoryBase64("security.email.aes-key-b64", aesKeyB64);
        int n = key.length;
        if (n != 16 && n != 24 && n != 32) {
            throw new IllegalStateException(
                    "security.email.aes-key-b64 must decode to 16/24/32 bytes, got " + n);
        }
        byte[] pep = decodeMandatoryBase64("security.email.hmac-pepper-b64", hmacPepperB64);
        if (pep.length < 16) {
            throw new IllegalStateException("security.email.hmac-pepper-b64 too short (<16 bytes)");
        }
        if (legacyPeppersB64 != null) {
            for (String legacy : legacyPeppersB64) {
                if (legacy == null || legacy.isBlank()) continue;
                byte[] decoded = decodeMandatoryBase64("security.email.legacy-peppers-b64", legacy);
                if (decoded.length < 16) {
                    throw new IllegalStateException("security.email.legacy-peppers-b64 entry too short (<16 bytes)");
                }
            }
        }
    }

    private byte[] pepper() {
        return Base64.getDecoder().decode(hmacPepperB64);
    }

    private byte[] aesKey() {
        return Base64.getDecoder().decode(aesKeyB64);
    }

    public String normalize(String email) {
        return EmailCryptoUtil.normalize(email);
    }

    public String hash(String normalizedEmail) {
        return hashWith(pepper(), normalizedEmail);
    }

    public List<String> hashCandidates(String normalizedEmail) {
        if (normalizedEmail == null) return List.of();
        LinkedHashSet<String> hashes = new LinkedHashSet<>();
        hashes.add(hashWith(pepper(), normalizedEmail));
        if (legacyPeppersB64 != null) {
            for (String legacy : legacyPeppersB64) {
                if (legacy == null || legacy.isBlank()) continue;
                hashes.add(hashWith(Base64.getDecoder().decode(legacy), normalizedEmail));
            }
        }
        return List.copyOf(hashes);
    }

    public String encrypt(String normalizedEmail) {
        return EmailCryptoUtil.aesGcmEncryptB64(aesKey(), normalizedEmail);
    }

    public String decrypt(String emailEncB64) {
        return EmailCryptoUtil.aesGcmDecryptB64(aesKey(), emailEncB64);
    }

    public String maskForLog(String emailPlain) {
        if (emailPlain == null) return null;
        int at = emailPlain.indexOf('@');
        if (at <= 1) return "***";
        return emailPlain.charAt(0) + "***" + emailPlain.substring(at);
    }

    private static String hashWith(byte[] pepperBytes, String normalizedEmail) {
        return EmailCryptoUtil.hmacSha256Hex(pepperBytes, normalizedEmail);
    }

    private static byte[] decodeMandatoryBase64(String property, String b64) {
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException("Missing required property: " + property);
        }
        return Base64.getDecoder().decode(b64);
    }
}
