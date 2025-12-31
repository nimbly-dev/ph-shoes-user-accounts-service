package com.nimbly.phshoesbackend.useraccount.core.config;

import com.nimbly.phshoesbackend.useraccount.core.config.props.AppAwsProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.File;
import java.net.URI;
import java.time.Duration;


@Slf4j
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
        SdkHttpClient http = ApacheHttpClient.builder()
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(2))
                .socketTimeout(Duration.ofSeconds(5))
                .build();

        var b = DynamoDbClient.builder()
                .httpClient(http)
                .credentialsProvider(credentialsProvider)
                .region(Region.of(aws.getRegion()));

        if (StringUtils.hasText(aws.getEndpoint())) {
            b = b.endpointOverride(normalizeEndpoint(aws.getEndpoint()));
        }

        var client = b.build();
        log.info("ddb.client region={} endpoint={}",
                aws.getRegion(),
                StringUtils.hasText(aws.getEndpoint()) ? normalizeEndpoint(aws.getEndpoint()) : "(aws)");
        return client;
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient low) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(low).build();
    }

    /** Ensure scheme/port, and map 'localstack' -> 'localhost' when not in a container. */
    private static URI normalizeEndpoint(String raw) {
        if (!StringUtils.hasText(raw)) return null;

        // Prepend scheme if missing
        String url = raw.matches("^[a-zA-Z]+://.*") ? raw : "http://" + raw;

        // If running on host JVM but endpoint says 'localstack', rewrite to localhost
        boolean inContainer = new File("/.dockerenv").exists();
        if (url.startsWith("http://localstack") && !inContainer) {
            url = url.replaceFirst("http://localstack", "http://localhost");
        }

        // Add LocalStack default port if none specified
        if (url.matches("^http://(localhost|localstack)(/.*)?$")) {
            url = url.replaceFirst("^(http://[^/:]+)", "$1:4566");
        }

        return URI.create(url);
    }
}
