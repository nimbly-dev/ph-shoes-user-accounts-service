package com.nimbly.phshoesbackend.useraccount.config;

import com.nimbly.phshoesbackend.useraccount.service.DynamoSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "schema.auto-create", havingValue = "true"
)
public class SchemaInitRunner {

    @Autowired
    private final DynamoSchemaService schemaService;
    private final Environment env;

    @Bean
    ApplicationRunner runSchemaInit() {
        return args -> {
            log.info("SchemaInitRunner starting...");
            schemaService.ensureBaseSchema();
            schemaService.ensureAuthSessionsTable();
            schemaService.ensureVerificationTable();
            schemaService.ensureSuppressionTable();
            log.info("SchemaInitRunner done.");
        };
    }
}
