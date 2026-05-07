package com.mcp.indexing.config;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
@EnableConfigurationProperties(ConcurrencyConfiguration.DbExecutorProperties.class)
public class ConcurrencyConfiguration {
    @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new VirtualThreadTaskExecutor("mcp-vt-");
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService jdbcExecutor(DbExecutorProperties properties) {
        ThreadFactory factory = Thread.ofPlatform().name("mcp-jdbc-", 0).factory();
        return Executors.newFixedThreadPool(properties.maxConcurrency(), factory);
    }

    @ConfigurationProperties(prefix = "mcp.db")
    public record DbExecutorProperties(int maxConcurrency) {
        public DbExecutorProperties() {
            this(8);
        }
    }
}

