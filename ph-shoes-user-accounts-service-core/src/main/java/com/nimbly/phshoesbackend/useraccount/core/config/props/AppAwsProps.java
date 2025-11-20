package com.nimbly.phshoesbackend.useraccount.core.config.props;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aws")
public class AppAwsProps {
    private String region;
    private String endpoint;
}
