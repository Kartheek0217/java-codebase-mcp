package com.mcp.config;

import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class VirtualThreadConfig {

    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("mcp-worker-", 0).factory()));
    }

    @Bean
    public Cache<String, java.util.List<com.mcp.entity.Symbol>> symbolCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(java.time.Duration.ofMinutes(10))
                .recordStats()
                .build();
    }
}
