package com.nimbly.phshoesbackend.useraccount.core.unsubscribe.impl;

import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidVerificationTokenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HmacUnsubscribeTokenCodecTest {

    @Test
    void encodeDecode_roundTrip() {
        // Arrange
        AppVerificationProps verificationProps = new AppVerificationProps();
        verificationProps.setSecret("secret");
        HmacUnsubscribeTokenCodec codec = new HmacUnsubscribeTokenCodec(verificationProps);

        // Act
        String token = codec.encode("hash123");
        String decoded = codec.decodeAndVerify(token);

        // Assert
        assertEquals("hash123", decoded);
    }

    @Test
    void decodeAndVerify_throwsWhenFormatInvalid() {
        // Arrange
        AppVerificationProps verificationProps = new AppVerificationProps();
        verificationProps.setSecret("secret");
        HmacUnsubscribeTokenCodec codec = new HmacUnsubscribeTokenCodec(verificationProps);

        // Act
        InvalidVerificationTokenException exception = assertThrows(InvalidVerificationTokenException.class, () -> codec.decodeAndVerify("bad"));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void decodeAndVerify_throwsWhenLegacyJwtProvided() {
        // Arrange
        AppVerificationProps verificationProps = new AppVerificationProps();
        verificationProps.setSecret("secret");
        HmacUnsubscribeTokenCodec codec = new HmacUnsubscribeTokenCodec(verificationProps);

        // Act
        InvalidVerificationTokenException exception = assertThrows(InvalidVerificationTokenException.class,
                () -> codec.decodeAndVerify("legacy.part.signature"));

        // Assert
        assertNotNull(exception);
    }
}
