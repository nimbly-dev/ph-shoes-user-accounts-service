package com.nimbly.phshoesbackend.useraccount.service.impl;

import com.nimbly.phshoesbackend.useraccount.config.AppTagProps;
import com.nimbly.phshoesbackend.useraccount.service.DynamoSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DynamoSchemaServiceImpl implements DynamoSchemaService {

    private final DynamoDbClient ddb;
    private final AppTagProps tags;

    private static final String ACCOUNTS   = "accounts";
    private static final String MIGRATIONS = "dynamodb_migrations";
    private static final String GSI_EMAIL  = "gsi_email";

    @Override
    public void ensureBaseSchema() throws InterruptedException {
        ensureAccounts();
        ensureMigrations();
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
        ddb.createTable(CreateTableRequest.builder()
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
                .tags(
                        Tag.builder().key("Project").value(tags.getProjectTag()).build(),
                        Tag.builder().key("Env").value(tags.getEnvTag()).build(),
                        Tag.builder().key("Service").value(tags.getServiceTag()).build()
                )
                .build());
    }

    private void ensureEmailGsi() throws InterruptedException {
        var desc = ddb.describeTable(b -> b.tableName(DynamoSchemaServiceImpl.ACCOUNTS)).table();
        var hasGsi = Optional.ofNullable(desc.globalSecondaryIndexes()).orElse(List.of())
                .stream().anyMatch(g -> GSI_EMAIL.equals(g.indexName()));
        if (!hasGsi) {
            ddb.updateTable(UpdateTableRequest.builder()
                    .tableName(DynamoSchemaServiceImpl.ACCOUNTS)
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
            waitActive(DynamoSchemaServiceImpl.ACCOUNTS);
        }
    }

    /* ====== migrations ====== */
    private void ensureMigrations() throws InterruptedException {
        if (!exists(MIGRATIONS)) {
            ddb.createTable(CreateTableRequest.builder()
                    .tableName(MIGRATIONS)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("migrationId").attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder().attributeName("migrationId").keyType(KeyType.HASH).build())
                    .tags(
                            Tag.builder().key("Project").value(tags.getProjectTag()).build(),
                            Tag.builder().key("Env").value(tags.getEnvTag()).build(),
                            Tag.builder().key("Service").value(tags.getServiceTag()).build()
                    )
                    .build());
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
        while (true) {
            var status = ddb.describeTable(b -> b.tableName(table)).table().tableStatus();
            if (status == TableStatus.ACTIVE) return;
            Thread.sleep(700);
        }
    }

    private String tableArn(String table) {
        return ddb.describeTable(b -> b.tableName(table)).table().tableArn();
    }

    private void ensureResourceTags(String arn) {
        var current = ddb.listTagsOfResource(ListTagsOfResourceRequest.builder().resourceArn(arn).build())
                .tags().stream().collect(Collectors.toMap(Tag::key, Tag::value));
        Map<String, String> required = Map.of(
                "Project", tags.getProjectTag(),
                "Env", tags.getEnvTag()
        );
        var missing = required.entrySet().stream()
                .filter(e -> !e.getValue().equals(current.get(e.getKey())))
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .toList();
        if (!missing.isEmpty()) {
            ddb.tagResource(TagResourceRequest.builder().resourceArn(arn).tags(missing).build());
        }
    }
}
