package com.mcp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mcp.codebase")
public class CodebaseProperties {

    /**
     * Maximum file size in KB before auto-downgrading to structure format.
     * Default: 200 KB.
     */
    private int maxFileSizeKb = 200;

    public int getMaxFileSizeKb() {
        return maxFileSizeKb;
    }

    public void setMaxFileSizeKb(int maxFileSizeKb) {
        this.maxFileSizeKb = maxFileSizeKb;
    }
}