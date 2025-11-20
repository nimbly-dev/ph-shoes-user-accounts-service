package com.nimbly.phshoesbackend.useraccount.core.unsubscribe;

public interface UnsubscribeTokenCodec {
    String encode(String emailHash);

    String decodeAndVerify(String token);
}

