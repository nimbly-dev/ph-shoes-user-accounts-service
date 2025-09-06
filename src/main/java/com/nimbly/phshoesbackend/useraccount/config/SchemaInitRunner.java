package com.nimbly.phshoesbackend.useraccount.config;

import com.nimbly.phshoesbackend.useraccount.service.DynamoSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration                   // ← add this
@RequiredArgsConstructor         // ← use constructor injection
public class SchemaInitRunner {

    @Autowired
    private DynamoSchemaService schemaService;

    @Bean
    ApplicationRunner runSchemaInit() {
        return args -> {
            log.info("SchemaInitRunner starting...");
            schemaService.ensureBaseSchema();
            log.info("SchemaInitRunner done.");
        };
    }
}
