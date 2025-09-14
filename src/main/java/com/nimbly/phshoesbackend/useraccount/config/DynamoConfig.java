package com.nimbly.phshoesbackend.useraccount.config;

import com.nimbly.phshoesbackend.useraccount.config.AppAwsProps; // adjust if your props package differs
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
public class DynamoConfig {

    private final AppAwsProps aws;

    public DynamoConfig(AppAwsProps aws) { this.aws = aws; }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (StringUtils.hasText(aws.getEndpoint())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public DynamoDbClient dynamoDbClient(AwsCredentialsProvider credentialsProvider) {
        var b = DynamoDbClient.builder().credentialsProvider(credentialsProvider)
                .region(Region.of(aws.getRegion()));
        if (StringUtils.hasText(aws.getEndpoint())) b = b.endpointOverride(URI.create(aws.getEndpoint()));
        return b.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient low) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(low).build();
    }
}
