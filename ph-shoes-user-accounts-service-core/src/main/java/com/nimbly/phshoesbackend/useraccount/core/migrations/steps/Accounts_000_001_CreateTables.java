package com.nimbly.phshoesbackend.useraccount.core.migrations.steps;

import com.nimbly.phshoesbackend.commons.core.migrations.UpgradeContext;
import com.nimbly.phshoesbackend.commons.core.migrations.UpgradeStep;
import com.nimbly.phshoesbackend.commons.core.migrations.utility.TableCreator;
import com.nimbly.phshoesbackend.useraccount.core.model.dynamo.AccountAttrs;
import com.nimbly.phshoesbackend.useraccount.core.model.dynamo.SessionAttrs;
import com.nimbly.phshoesbackend.commons.core.model.dynamo.SuppressionAttrs;
import com.nimbly.phshoesbackend.useraccount.core.model.dynamo.VerificationAttrs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

@Component
@RequiredArgsConstructor
public class Accounts_000_001_CreateTables implements UpgradeStep {

    private static final BillingMode BILLING_MODE = BillingMode.PROVISIONED;
    private static final long DEFAULT_RCU = 1L;
    private static final long DEFAULT_WCU = 1L;

    private final TableCreator tables;

    @Override public String service()     { return "accounts_service"; }
    @Override public String fromVersion() { return "0.0.0"; }
    @Override public String toVersion()   { return "0.0.1"; }
    @Override public String description() { return "Create accounts, login_sessions, account_verifications, email_suppressions"; }

    @Override
    public void apply(UpgradeContext ctx) {
        final ScalarAttributeType S = ScalarAttributeType.S;

        // ===== accounts =====
        final String accounts = ctx.tbl(AccountAttrs.TABLE);
        tables.createTableIfNotExists(
                accounts,
                List.of(AttributeDefinition.builder().attributeName(AccountAttrs.PK_USERID).attributeType(S).build()),
                List.of(KeySchemaElement.builder().attributeName(AccountAttrs.PK_USERID).keyType(KeyType.HASH).build()),
                BILLING_MODE, DEFAULT_RCU, DEFAULT_WCU
        );
        // Keep GSIs aligned with the base table billing mode (PROVISIONED for free-tier eligibility)
        tables.createGsiIfNotExists(accounts, AccountAttrs.GSI_EMAIL, AccountAttrs.EMAIL_HASH, S,
                BILLING_MODE, DEFAULT_RCU, DEFAULT_WCU);

        // ===== login_sessions =====
        final String sessions = ctx.tbl(SessionAttrs.TABLE);
        tables.createTableIfNotExists(
                sessions,
                List.of(AttributeDefinition.builder().attributeName(SessionAttrs.PK_SESSION).attributeType(S).build()),
                List.of(KeySchemaElement.builder().attributeName(SessionAttrs.PK_SESSION).keyType(KeyType.HASH).build()),
                BILLING_MODE, DEFAULT_RCU, DEFAULT_WCU
        );
        tables.createGsiIfNotExists(sessions, "gsi_userId", SessionAttrs.USER_ID, S,
                BILLING_MODE, DEFAULT_RCU, DEFAULT_WCU);
        tables.enableTtlIfDisabled(sessions, SessionAttrs.EXPIRES_AT);

        // ===== account_verifications =====
        final String verifs = ctx.tbl(VerificationAttrs.TABLE);
        tables.createTableIfNotExists(
                verifs,
                List.of(AttributeDefinition.builder().attributeName(VerificationAttrs.PK_VERIFICATION_ID).attributeType(S).build()),
                List.of(KeySchemaElement.builder().attributeName(VerificationAttrs.PK_VERIFICATION_ID).keyType(KeyType.HASH).build()),
                BILLING_MODE, DEFAULT_RCU, DEFAULT_WCU
        );
        tables.createGsiIfNotExists(verifs, "gsi_userId", VerificationAttrs.USER_ID, S,
                BILLING_MODE, DEFAULT_RCU, DEFAULT_WCU);
        tables.createGsiIfNotExists(verifs, VerificationAttrs.GSI_EMAIL, VerificationAttrs.EMAIL_HASH, S,
                BILLING_MODE, DEFAULT_RCU, DEFAULT_WCU);
        tables.enableTtlIfDisabled(verifs, VerificationAttrs.EXPIRES_AT);

        // ===== email_suppressions =====
        final String suppressions = ctx.tbl(SuppressionAttrs.TABLE);
        tables.createTableIfNotExists(
                suppressions,
                List.of(AttributeDefinition.builder().attributeName(SuppressionAttrs.PK_EMAIL_HASH).attributeType(S).build()),
                List.of(KeySchemaElement.builder().attributeName(SuppressionAttrs.PK_EMAIL_HASH).keyType(KeyType.HASH).build()),
                BILLING_MODE, DEFAULT_RCU, DEFAULT_WCU
        );
        tables.enableTtlIfDisabled(suppressions, SuppressionAttrs.EXPIRES_AT);
    }
}
