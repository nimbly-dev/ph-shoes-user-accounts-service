package com.nimbly.phshoesbackend.useraccount.service;

public interface DynamoSchemaService {
    void ensureBaseSchema() throws InterruptedException;

    int backfillAccountSettingsDefault(int maxUpdates);

    void ensureVerificationTable();
    void ensureAuthSessionsTable();
    void ensureSuppressionTable();
}
