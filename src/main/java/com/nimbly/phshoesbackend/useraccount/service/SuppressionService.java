package com.nimbly.phshoesbackend.useraccount.service;

import com.nimbly.phshoesbackend.useraccount.model.SuppressionReason;

public interface SuppressionService {
    public boolean shouldBlock(String email);
    public void suppress(String email, SuppressionReason reason, String source, String notes, Long ttlEpochSeconds);
    public void unsuppress(String email);
}
