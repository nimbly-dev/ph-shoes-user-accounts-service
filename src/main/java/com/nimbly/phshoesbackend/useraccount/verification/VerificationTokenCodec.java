package com.nimbly.phshoesbackend.useraccount.verification;

import com.nimbly.phshoesbackend.useraccount.exception.InvalidVerificationTokenException;

public interface VerificationTokenCodec {
    String encode(String verificationId);
    String decodeAndVerify(String token) throws InvalidVerificationTokenException;
}
