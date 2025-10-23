package com.nimbly.phshoesbackend.useraccount.migrations.steps;

import com.nimbly.phshoesbackend.services.common.core.migrations.UpgradeContext;
import com.nimbly.phshoesbackend.services.common.core.migrations.UpgradeStep;
import com.nimbly.phshoesbackend.services.common.core.migrations.utility.TableCreator;
import com.nimbly.phshoesbackend.services.common.core.model.dynamo.AccountAttrs;
import com.nimbly.phshoesbackend.services.common.core.model.dynamo.SuppressionAttrs;
import com.nimbly.phshoesbackend.services.common.core.model.dynamo.VerificationAttrs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.List;

/**
 * 0.0.0 → 0.0.1 baseline for Accounts:
 * Creates core tables, then adds GSIs and enables TTL where needed. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class Accounts_000_001_CreateTables implements UpgradeStep {

    private final TableCreator tables;

    @Override public String service()     { return "accounts_service"; }
    @Override public String fromVersion() { return "0.0.0"; }
    @Override public String toVersion()   { return "0.0.1"; }
    @Override public String description() { return "Create accounts/auth_sessions/account_verifications/email_suppressions with correct PKs, GSIs, TTL"; }

    @Override
    public void apply(UpgradeContext ctx) {
        final ScalarAttributeType S = ScalarAttributeType.S;

        // ===== accounts =====
        // PK: userid ; GSI: gsi_email(email)
        final String accounts = ctx.tbl(AccountAttrs.TABLE);
        tables.createTableIfNotExists(
                accounts,
                List.of(AttributeDefinition.builder().attributeName(AccountAttrs.PK_USERID).attributeType(S).build()),
                List.of(KeySchemaElement.builder().attributeName(AccountAttrs.PK_USERID).keyType(KeyType.HASH).build()),
                null, null, null // PAY_PER_REQUEST by default
        );
        tables.createGsiIfNotExists(accounts, AccountAttrs.GSI_EMAIL, AccountAttrs.EMAIL_HASH, S, null, null, null);

        // ===== auth_sessions =====
        // PK: jti ; GSI: gsi_userId(userId) ; TTL: ttl
        final String sessions = ctx.tbl("auth_sessions");
        tables.createTableIfNotExists(
                sessions,
                List.of(AttributeDefinition.builder().attributeName("jti").attributeType(S).build()),
                List.of(KeySchemaElement.builder().attributeName("jti").keyType(KeyType.HASH).build()),
                null, null, null
        );
        tables.createGsiIfNotExists(sessions, "gsi_userId", "userId", S, null, null, null);
        tables.enableTtlIfDisabled(sessions, "ttl");

        // ===== account_verifications =====
        // PK: verificationId ; GSI: gsi_userId(userId) ; TTL: expiresAt
        final String verifs = ctx.tbl(VerificationAttrs.TABLE);
        tables.createTableIfNotExists(
                verifs,
                List.of(AttributeDefinition.builder().attributeName(VerificationAttrs.PK_VERIFICATION_ID).attributeType(S).build()),
                List.of(KeySchemaElement.builder().attributeName(VerificationAttrs.PK_VERIFICATION_ID).keyType(KeyType.HASH).build()),
                null, null, null
        );
        tables.createGsiIfNotExists(verifs, "gsi_userId", VerificationAttrs.USER_ID, S, null, null, null);
        tables.enableTtlIfDisabled(verifs, VerificationAttrs.EXPIRES_AT);

        // ===== email_suppressions =====
        // PK: email ; TTL: expires_at
        final String suppressions = ctx.tbl(SuppressionAttrs.TABLE);
        tables.createTableIfNotExists(
                suppressions,
                List.of(AttributeDefinition.builder().attributeName(SuppressionAttrs.PK_EMAIL).attributeType(S).build()),
                List.of(KeySchemaElement.builder().attributeName(SuppressionAttrs.PK_EMAIL).keyType(KeyType.HASH).build()),
                null, null, null
        );
        tables.enableTtlIfDisabled(suppressions, SuppressionAttrs.EXPIRES_AT);
    }
}