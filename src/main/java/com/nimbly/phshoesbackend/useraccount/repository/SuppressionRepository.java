package com.nimbly.phshoesbackend.useraccount.repository;

import com.nimbly.phshoesbackend.useraccount.model.SuppressionEntry;

public interface SuppressionRepository {
    boolean isSuppressed(String email);
    void put(SuppressionEntry entry);
    void remove(String email); // admin un-suppress
    SuppressionEntry get(String email);
}
