package com.nimbly.phshoesbackend.useraccount.core.subscribe.impl;

import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.subscribe.SubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeTokenCodec;
import com.nimbly.phshoesbackend.useraccount.core.util.SensitiveValueMasker;
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
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        String emailHash = unsubscribeTokenCodec.decodeAndVerify(token);
        suppressionService.unsuppressHash(emailHash);
        log.info("subscribe.success hashPrefix={}", SensitiveValueMasker.hashPrefix(emailHash));
    }
}

