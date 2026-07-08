package com.skep.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        CorsProperties.class,
        BootstrapAdminProperties.class,
        com.skep.onlyoffice.OnlyOfficeProperties.class,
})
public class AppPropertiesConfig {
}
