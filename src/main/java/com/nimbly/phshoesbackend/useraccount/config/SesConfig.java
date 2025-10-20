package com.nimbly.phshoesbackend.useraccount.config;

import java.net.URI;
import java.time.Duration;

import com.nimbly.phshoesbackend.useraccount.config.props.AppAwsProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
public class SesConfig {

    private final AppAwsProps props;

    public SesConfig(AppAwsProps props) {
        this.props = props;
    }

    @Bean
    public SesV2Client sesV2Client() {
        var http = ApacheHttpClient.builder()
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(2))
                .socketTimeout(Duration.ofSeconds(5))
                .build();

        var override = ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(Duration.ofSeconds(3))
                .apiCallTimeout(Duration.ofSeconds(10))
                .build();

        var builder = SesV2Client.builder()
                .httpClient(http)
                .overrideConfiguration(override)
                .region(Region.of(props.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (props.getEndpoint() != null && !props.getEndpoint().isBlank()) {
            builder = builder.endpointOverride(URI.create(props.getEndpoint()));
        }

        return builder.build();
    }
}