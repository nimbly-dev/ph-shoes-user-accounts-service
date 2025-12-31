package com.nimbly.phshoesbackend.useraccount.core.verification.impl;

import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidVerificationTokenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HmacVerificationTokenCodecTest {

    @Test
    void encodeDecode_roundTrip() {
        // Arrange
        AppVerificationProps props = new AppVerificationProps();
        props.setSecret("secret");
        HmacVerificationTokenCodec codec = new HmacVerificationTokenCodec(props);

        // Act
        String token = codec.encode("verify-1");
        String decoded = codec.decodeAndVerify(token);

        // Assert
        assertEquals("verify-1", decoded);
    }

    @Test
    void decodeAndVerify_throwsWhenSignatureInvalid() {
        // Arrange
        AppVerificationProps props = new AppVerificationProps();
        props.setSecret("secret");
        HmacVerificationTokenCodec codec = new HmacVerificationTokenCodec(props);
        String token = codec.encode("verify-1");
        String tampered = token.substring(0, token.length() - 2) + "aa";

        // Act
        InvalidVerificationTokenException exception = assertThrows(InvalidVerificationTokenException.class, () -> codec.decodeAndVerify(tampered));

        // Assert
        assertNotNull(exception);
    }
}
