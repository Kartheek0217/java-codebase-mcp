package com.mcp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Java Codebase MCP API")
                        .version("0.0.1")
                        .description("Middleware for indexing Java codebases and extracting symbols for MCP.")
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")));
    }
}
