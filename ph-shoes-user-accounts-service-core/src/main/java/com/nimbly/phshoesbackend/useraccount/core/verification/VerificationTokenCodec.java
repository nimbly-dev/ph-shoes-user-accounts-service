package com.nimbly.phshoesbackend.useraccount.core.verification;

import com.nimbly.phshoesbackend.useraccount.core.exception.InvalidVerificationTokenException;

public interface VerificationTokenCodec {
    String encode(String verificationId);
    String decodeAndVerify(String token) throws InvalidVerificationTokenException;
}
