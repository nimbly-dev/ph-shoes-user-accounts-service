package com.nimbly.phshoesbackend.useraccount.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "schema.migration")
public class SchemaMigrationsProps {
    private boolean enabled;
    private boolean revertOnError;
    private boolean bootstrapIfMissing;
    private String initial;
    private String tablePrefix;
    private String serviceName;
}
