package com.nimbly.phshoesbackend.useraccount.unsubscribe;

public interface UnsubscribeTokenCodec {
    String encode(String emailHash);

    String decodeAndVerify(String token);
}

