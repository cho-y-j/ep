package com.example.verifyapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Value("${kosha.timeout:5000}")
    private int koshaTimeout;

    @Value("${google.vision.timeout:10000}")
    private int visionTimeout;

    @Value("${rims.timeout:5000}")
    private int rimsTimeout;

    @Bean(name = "koshaRestTemplate")
    public RestTemplate koshaRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(koshaTimeout))
                .setReadTimeout(Duration.ofMillis(koshaTimeout))
                .build();
    }

    @Bean(name = "visionRestTemplate")
    public RestTemplate visionRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(visionTimeout))
                .setReadTimeout(Duration.ofMillis(visionTimeout))
                .build();
    }

    @Bean(name = "rimsRestTemplate")
    public RestTemplate rimsRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(rimsTimeout))
                .setReadTimeout(Duration.ofMillis(rimsTimeout))
                .build();
    }
}
