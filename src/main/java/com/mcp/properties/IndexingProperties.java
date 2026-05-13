package com.mcp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mcp.indexing")
public class IndexingProperties {

    /**
     * Number of concurrent files to index simultaneously.
     * Throttles database-bound tasks to prevent connection pool exhaustion.
     */
    private int concurrency = 15;

    /**
     * Number of concurrent worker threads for general background tasks.
     */
    private int workerConcurrency = 20;

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getWorkerConcurrency() {
        return workerConcurrency;
    }

    public void setWorkerConcurrency(int workerConcurrency) {
        this.workerConcurrency = workerConcurrency;
    }
}
