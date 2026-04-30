package com.skep.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "skep.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
