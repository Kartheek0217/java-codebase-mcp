package com.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAdapterConfig {

    private final OpenAdapterProperties properties;

    public OpenAdapterConfig(OpenAdapterProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RestClient openAdapterRestClient() {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
