package com.nimbly.phshoesbackend.useraccount.core.subscribe.impl;

import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.subscribe.SubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeTokenCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeServiceImpl implements SubscribeService {

    private final UnsubscribeTokenCodec unsubscribeTokenCodec;
    private final SuppressionService suppressionService;

    @Override
    public void resubscribe(String token) {
        String emailHash = decodeToken(token);
        suppressionService.unsuppressHash(emailHash);
        log.info("subscribe.success hashPrefix={}", shortHash(emailHash));
    }

    private String decodeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        return unsubscribeTokenCodec.decodeAndVerify(token);
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "(blank)";
        }
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }
}
