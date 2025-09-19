package com.nimbly.phshoesbackend.useraccount.service;

public interface DynamoSchemaService {
    void ensureBaseSchema() throws InterruptedException;
    void ensureVerificationTable();
    void ensureAuthSessionsTable();
}
