package com.nimbly.phshoesbackend.useraccount.core.service;


import com.nimbly.phshoesbackend.services.common.core.model.SuppressionReason;

public interface SuppressionService {
    boolean shouldBlock(String emailPlain);

    void suppress(String emailPlain,
                  SuppressionReason reason,
                  String source,
                  String notes,
                  Long ttlEpochSeconds);

    void suppressHash(String emailHash,
                      SuppressionReason reason,
                      String source,
                      String notes,
                      Long ttlEpochSeconds);

    void unsuppress(String emailPlain);

    void unsuppressHash(String emailHash);
}