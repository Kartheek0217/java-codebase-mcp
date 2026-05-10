package com.mcp.config;

import java.util.concurrent.Executors;
import java.util.List;
import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mcp.entity.Symbol;

@Configuration
public class VirtualThreadConfig {

	@Bean
	public AsyncTaskExecutor applicationTaskExecutor() {
		return new TaskExecutorAdapter(
				Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("mcp-worker-", 0).factory()));
	}

	@Bean
	public CacheManager cacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager("topology", "symbols");
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(1000)
				.expireAfterAccess(Duration.ofMinutes(30))
				.recordStats());
		return cacheManager;
	}

	@Bean
	public Cache<String, List<Symbol>> symbolCache() {
		return Caffeine.newBuilder()
				.maximumSize(50_000)
				.expireAfterAccess(Duration.ofMinutes(60))
				.recordStats()
				.build();
	}
}
