package com.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mcp")
public class ScannerConfig {

    private final Scan scan = new Scan();
    private final Scanner scanner = new Scanner();

    public Scan getScan() { return scan; }
    public Scanner getScanner() { return scanner; }

    public static class Scan {
        private String directory = "src/main/java";
        private String extensions = ".java";
        private long reconciliationInterval = 3600000;

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }

        public String getExtensions() { return extensions; }
        public void setExtensions(String extensions) { this.extensions = extensions; }

        public long getReconciliationInterval() { return reconciliationInterval; }
        public void setReconciliationInterval(long reconciliationInterval) { this.reconciliationInterval = reconciliationInterval; }
    }

    public static class Scanner {
        private int batchSize = 100;

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }
}
