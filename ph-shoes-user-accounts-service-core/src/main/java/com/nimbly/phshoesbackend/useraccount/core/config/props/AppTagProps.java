package com.nimbly.phshoesbackend.useraccount.core.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppTagProps {
    private String projectTag;
    private String envTag;
    private String serviceTag;
}
