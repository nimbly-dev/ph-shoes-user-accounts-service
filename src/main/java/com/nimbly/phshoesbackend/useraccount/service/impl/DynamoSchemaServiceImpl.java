package com.nimbly.phshoesbackend.useraccount.service.impl;

import com.nimbly.phshoesbackend.services.common.core.model.dynamo.AccountAttrs;
import com.nimbly.phshoesbackend.services.common.core.model.dynamo.SuppressionAttrs;
import com.nimbly.phshoesbackend.services.common.core.model.dynamo.VerificationAttrs;
import com.nimbly.phshoesbackend.useraccount.config.DynamoConfigTables;
import com.nimbly.phshoesbackend.useraccount.config.props.AppTagProps;
import com.nimbly.phshoesbackend.useraccount.service.DynamoSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamoSchemaServiceImpl implements DynamoSchemaService {

    private final DynamoDbClient ddb;
    private final DynamoDbEnhancedClient enhanced;
    private final DynamoConfigTables tables;
    private final AppTagProps tags;

    private static final String ACCOUNTS   = "accounts";
    private static final String MIGRATIONS = "dynamodb_migrations";
    private static final String GSI_EMAIL  = "gsi_email";

    @Override
    public void ensureBaseSchema() throws InterruptedException {
        ensureAccounts();
        ensureMigrations();
        // Call ensureVerificationTable() here as well if you want it always created in dev:
        // ensureVerificationTable();
    }


    @Override
    public int backfillAccountSettingsDefault(int maxUpdates) {
        final String defaultJson = """
            {"Notification_Email_Preferences":{"Email_Notifications":true}}
            """;

        int updated = 0;
        Map<String, AttributeValue> startKey = null;

        do {
            ScanRequest.Builder sb = ScanRequest.builder()
                    .tableName(AccountAttrs.TABLE)
                    .projectionExpression("#id, #s")
                    .filterExpression("attribute_not_exists(#s)")
                    .expressionAttributeNames(Map.of("#id", AccountAttrs.PK_USERID, "#s", AccountAttrs.SETTINGS_JSON))
                    .limit(Math.min(100, Math.max(1, maxUpdates - updated)));

            if (startKey != null) sb.exclusiveStartKey(startKey);
            ScanResponse scan = ddb.scan(sb.build());

            for (var item : scan.items()) {
                String userId = item.get(AccountAttrs.PK_USERID).s();
                try {
                    ddb.updateItem(UpdateItemRequest.builder()
                            .tableName(AccountAttrs.TABLE)
                            .key(Map.of(AccountAttrs.PK_USERID, AttributeValue.builder().s(userId).build()))
                            .conditionExpression("attribute_not_exists(#s)")
                            .updateExpression("SET #s = :json, #u = :now")
                            .expressionAttributeNames(Map.of(
                                    "#s", AccountAttrs.SETTINGS_JSON,
                                    "#u", AccountAttrs.UPDATED_AT
                            ))
                            .expressionAttributeValues(Map.of(
                                    ":json", AttributeValue.builder().s(defaultJson).build(),
                                    ":now",  AttributeValue.builder().s(Instant.now().toString()).build()
                            ))
                            .build());
                    updated++;
                    if (updated >= maxUpdates) break;
                } catch (ConditionalCheckFailedException ignored) {
                    // someone wrote settings concurrently, ignore
                }
            }

            startKey = scan.lastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty() && updated < maxUpdates);

        if (updated > 0) {
            log.info("Account settings backfill wrote {} items (cap={})", updated, maxUpdates);
        } else {
            log.info("Account settings backfill: nothing to do");
        }
        return updated;
    }

    private void waitForActive(String table) {
        for (int i = 0; i < 30; i++) {
            try {
                var desc = ddb.describeTable(DescribeTableRequest.builder().tableName(table).build());
                if (desc.table().tableStatus() == TableStatus.ACTIVE) return;
                Thread.sleep(1000);
            } catch (Exception ignored) {}
        }
        log.warn("Timed out waiting for table ACTIVE: {}", table);
    }

    @Override
    public void ensureVerificationTable() {
        final String table = VerificationAttrs.TABLE;
        try {
            ddb.describeTable(DescribeTableRequest.builder().tableName(table).build());
            log.info("DynamoDB table {} already exists", table);
            return;
        } catch (ResourceNotFoundException rnfe) {
            log.info("Creating DynamoDB table {}", table);
        }

        ddb.createTable(CreateTableRequest.builder()
                .tableName(table)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("verificationId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("userId").attributeType(ScalarAttributeType.S).build()
                )
                .keySchema(KeySchemaElement.builder().attributeName("verificationId").keyType(KeyType.HASH).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("gsi_userId")
                        .keySchema(KeySchemaElement.builder().attributeName("userId").keyType(KeyType.HASH).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                .build());

        // Wait until ACTIVE (SDK v2 waiter)
        DynamoDbWaiter waiter = ddb.waiter();
        waiter.waitUntilTableExists(DescribeTableRequest.builder().tableName(table).build());
        log.info("Created table {}", table);

        // Enable TTL for cleanup
        ddb.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                .tableName(table)
                .timeToLiveSpecification(TimeToLiveSpecification.builder()
                        .attributeName("expiresAt")
                        .enabled(true)
                        .build())
                .build());
    }

    @Override
    public void ensureAuthSessionsTable() {
        final String table = "auth_sessions";
        try {
            ddb.describeTable(b -> b.tableName(table));
            log.info("DynamoDB table {} already exists", table);
            return;
        } catch (ResourceNotFoundException ignored) {
            log.info("Creating DynamoDB table {}", table);
        }

        ddb.createTable(CreateTableRequest.builder()
                .tableName(table)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("jti").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("userId").attributeType(ScalarAttributeType.S).build()
                )
                .keySchema(KeySchemaElement.builder().attributeName("jti").keyType(KeyType.HASH).build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("gsi_userId")
                        .keySchema(KeySchemaElement.builder().attributeName("userId").keyType(KeyType.HASH).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                .build());

        ddb.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(table).build());
        log.info("Created table {}", table);

        ddb.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                .tableName(table)
                .timeToLiveSpecification(TimeToLiveSpecification.builder()
                        .attributeName("ttl")
                        .enabled(true)
                        .build())
                .build());
    }

    @Override
    public void ensureSuppressionTable() {
        ListTablesResponse tables = ddb.listTables();
        if (!tables.tableNames().contains(SuppressionAttrs.TABLE)) {
            log.info("Creating suppression table={}", SuppressionAttrs.TABLE);
            ddb.createTable(CreateTableRequest.builder()
                    .tableName(SuppressionAttrs.TABLE)
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName(SuppressionAttrs.PK_EMAIL).attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder()
                            .attributeName(SuppressionAttrs.PK_EMAIL).keyType(KeyType.HASH).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            waitForActive(SuppressionAttrs.TABLE);
        }

        // enable TTL
        try {
            DescribeTimeToLiveResponse ttl = ddb.describeTimeToLive(
                    DescribeTimeToLiveRequest.builder().tableName(SuppressionAttrs.TABLE).build());

            TimeToLiveStatus status = ttl.timeToLiveDescription() == null
                    ? TimeToLiveStatus.DISABLED
                    : ttl.timeToLiveDescription().timeToLiveStatus();

            if (status == TimeToLiveStatus.DISABLED) {
                ddb.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                        .tableName(SuppressionAttrs.TABLE)
                        .timeToLiveSpecification(TimeToLiveSpecification.builder()
                                .attributeName(SuppressionAttrs.EXPIRES_AT)
                                .enabled(true)
                                .build())
                        .build());
                log.info("TTL enabled on table={} attr={}", SuppressionAttrs.TABLE, SuppressionAttrs.EXPIRES_AT);
            } else {
                log.info("TTL already {} on table={}", status.toString(), SuppressionAttrs.TABLE);
            }
        } catch (ResourceNotFoundException e) {
            log.warn("Suppression table not found when enabling TTL: {}", SuppressionAttrs.TABLE);
        }
    }

    /* ====== accounts ====== */
    private void ensureAccounts() throws InterruptedException {
        if (!exists(ACCOUNTS)) {
            createAccounts();
            waitActive(ACCOUNTS);
        }
        ensureResourceTags(tableArn(ACCOUNTS));
        ensureEmailGsi();
    }

    private void createAccounts() {
        var req = CreateTableRequest.builder()
                .tableName(ACCOUNTS)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("userid").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("email").attributeType(ScalarAttributeType.S).build()
                )
                .keySchema(KeySchemaElement.builder().attributeName("userid").keyType(KeyType.HASH).build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName(GSI_EMAIL)
                        .keySchema(KeySchemaElement.builder().attributeName("email").keyType(KeyType.HASH).build())
                        .projection(Projection.builder().projectionType(ProjectionType.KEYS_ONLY).build())
                        .build())
                .build();

        // Add table tags only when present (avoid nulls)
        var tableTags = standardTags();
        if (!tableTags.isEmpty()) {
            req = req.toBuilder().tags(tableTags).build();
        }

        ddb.createTable(req);
    }

    private void ensureEmailGsi() throws InterruptedException {
        var desc = ddb.describeTable(b -> b.tableName(ACCOUNTS)).table();
        var hasGsi = Optional.ofNullable(desc.globalSecondaryIndexes()).orElse(List.of())
                .stream().anyMatch(g -> GSI_EMAIL.equals(g.indexName()));
        if (!hasGsi) {
            ddb.updateTable(UpdateTableRequest.builder()
                    .tableName(ACCOUNTS)
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("email").attributeType(ScalarAttributeType.S).build())
                    .globalSecondaryIndexUpdates(GlobalSecondaryIndexUpdate.builder()
                            .create(CreateGlobalSecondaryIndexAction.builder()
                                    .indexName(GSI_EMAIL)
                                    .keySchema(KeySchemaElement.builder().attributeName("email").keyType(KeyType.HASH).build())
                                    .projection(Projection.builder().projectionType(ProjectionType.KEYS_ONLY).build())
                                    .build())
                            .build())
                    .build());
            waitActive(ACCOUNTS);
        }
    }

    /* ====== migrations ====== */
    private void ensureMigrations() throws InterruptedException {
        if (!exists(MIGRATIONS)) {
            var req = CreateTableRequest.builder()
                    .tableName(MIGRATIONS)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("migrationId").attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder().attributeName("migrationId").keyType(KeyType.HASH).build())
                    .build();

            var tableTags = standardTags();
            if (!tableTags.isEmpty()) {
                req = req.toBuilder().tags(tableTags).build();
            }

            ddb.createTable(req);
            waitActive(MIGRATIONS);
        }
        ensureResourceTags(tableArn(MIGRATIONS));
    }

    /* ====== helpers ====== */
    private boolean exists(String table) {
        try {
            ddb.describeTable(b -> b.tableName(table));
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private void waitActive(String table) throws InterruptedException {
        int attempts = 0, maxAttempts = 120; // ~84 seconds at 700ms intervals
        while (attempts++ < maxAttempts) {
            var status = ddb.describeTable(b -> b.tableName(table)).table().tableStatus();
            if (status == TableStatus.ACTIVE) return;
            Thread.sleep(700);
        }
        throw new IllegalStateException("Table " + table + " did not become ACTIVE in time");
    }

    private String tableArn(String table) {
        try {
            var t = ddb.describeTable(b -> b.tableName(table)).table();
            return t != null ? t.tableArn() : null;
        } catch (ResourceNotFoundException rnfe) {
            return null;
        }
    }

    /** Null-safe resource tagging: skips if ARN or config values are missing. */
    private void ensureResourceTags(String arn) {
        if (arn == null || arn.isBlank()) return;

        var current = Optional.ofNullable(
                        ddb.listTagsOfResource(ListTagsOfResourceRequest.builder().resourceArn(arn).build()).tags()
                ).orElseGet(List::of)
                .stream()
                .filter(t -> t != null && t.key() != null && t.value() != null)
                .collect(Collectors.toMap(Tag::key, Tag::value, (a,b) -> b, LinkedHashMap::new));

        var desired = new LinkedHashMap<String,String>();
        putIfPresent(desired, "Project", tags.getProjectTag());
        putIfPresent(desired, "Env",     tags.getEnvTag());
        putIfPresent(desired, "Service", tags.getServiceTag());

        var toApply = desired.entrySet().stream()
                .filter(e -> !Objects.equals(current.get(e.getKey()), e.getValue()))
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .toList();

        if (!toApply.isEmpty()) {
            ddb.tagResource(TagResourceRequest.builder().resourceArn(arn).tags(toApply).build());
        }
    }

    /** Build standard table tags list with only non-blank values. */
    private List<Tag> standardTags() {
        var list = new ArrayList<Tag>(3);
        addTagIfPresent(list, "Project", tags.getProjectTag());
        addTagIfPresent(list, "Env",     tags.getEnvTag());
        addTagIfPresent(list, "Service", tags.getServiceTag());
        return list;
    }

    private static void putIfPresent(Map<String,String> m, String k, String v) {
        if (k != null && !k.isBlank() && v != null && !v.isBlank()) m.put(k, v);
    }

    private static void addTagIfPresent(List<Tag> target, String key, String value) {
        if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
            target.add(Tag.builder().key(key).value(value).build());
        }
    }
}
