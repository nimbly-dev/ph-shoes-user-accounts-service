package com.nimbly.phshoesbackend.useraccount.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DynamoConfigTables {
    @Value("${app.dynamo.tables:email_suppression}")
    private String suppressionsTableName;

    public String suppressionsTableName() {
        return suppressionsTableName;
    }
}
