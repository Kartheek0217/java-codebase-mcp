package com.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;

@Configuration
public class VirtualThreadConfig {

    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    public com.github.benmanes.caffeine.cache.Cache<String, java.util.List<com.mcp.entity.Symbol>> symbolCache() {
        return com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(java.time.Duration.ofMinutes(10))
            .recordStats()
            .build();
    }
}
