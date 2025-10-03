package com.nimbly.phshoesbackend.useraccount.verification.impl;

import com.nimbly.phshoesbackend.useraccount.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.exception.InvalidVerificationTokenException;
import com.nimbly.phshoesbackend.useraccount.verification.VerificationTokenCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class HmacVerificationTokenCodec implements VerificationTokenCodec {

    private final AppVerificationProps vprops;

    @Override
    public String encode(String verificationId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(vprops.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(verificationId.getBytes(StandardCharsets.UTF_8));
            return b64Url(verificationId.getBytes(StandardCharsets.UTF_8)) + "." + b64Url(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String decodeAndVerify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 2) throw new InvalidVerificationTokenException("Invalid token format");

        String id  = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        byte[] sig = Base64.getUrlDecoder().decode(parts[1]);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(vprops.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(id.getBytes(StandardCharsets.UTF_8));
            if (!constantTimeEquals(sig, expected)) {
                throw new InvalidVerificationTokenException("Invalid token signature");
            }
            return id;
        } catch (InvalidVerificationTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidVerificationTokenException("Token verification failure");
        }
    }

    private static String b64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int r = 0; for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }
}