package com.mcp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mcp.cache")
public class CacheProperties {

    private int topologyMaxSize = 1000;
    private int topologyExpireMinutes = 30;
    private int symbolCacheMaxSize = 50000;
    private int symbolCacheExpireMinutes = 60;

    public int getTopologyMaxSize() {
        return topologyMaxSize;
    }

    public void setTopologyMaxSize(int topologyMaxSize) {
        this.topologyMaxSize = topologyMaxSize;
    }

    public int getTopologyExpireMinutes() {
        return topologyExpireMinutes;
    }

    public void setTopologyExpireMinutes(int topologyExpireMinutes) {
        this.topologyExpireMinutes = topologyExpireMinutes;
    }

    public int getSymbolCacheMaxSize() {
        return symbolCacheMaxSize;
    }

    public void setSymbolCacheMaxSize(int symbolCacheMaxSize) {
        this.symbolCacheMaxSize = symbolCacheMaxSize;
    }

    public int getSymbolCacheExpireMinutes() {
        return symbolCacheExpireMinutes;
    }

    public void setSymbolCacheExpireMinutes(int symbolCacheExpireMinutes) {
        this.symbolCacheExpireMinutes = symbolCacheExpireMinutes;
    }
}
