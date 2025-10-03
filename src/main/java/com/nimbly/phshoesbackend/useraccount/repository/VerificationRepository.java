package com.nimbly.phshoesbackend.useraccount.repository;

import com.nimbly.phshoesbackend.useraccount.model.VerificationEntry;

import java.util.Optional;

public interface VerificationRepository {
    void put(VerificationEntry e);
    Optional<VerificationEntry> getById(String verificationId, boolean consistentRead);
    void markUsedIfPendingAndNotExpired(String verificationId, long nowEpochSeconds);
}
