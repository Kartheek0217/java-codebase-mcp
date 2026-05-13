package com.mcp.config;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mcp.entity.Symbol;
import com.mcp.properties.CacheProperties;
import com.mcp.properties.IndexingProperties;

@Configuration
public class VirtualThreadConfig {

	@Bean
	public AsyncTaskExecutor applicationTaskExecutor(IndexingProperties properties) {
		return new TaskExecutorAdapter(
				Executors.newFixedThreadPool(properties.getWorkerConcurrency(),
						Thread.ofVirtual().name("mcp-worker-", 0).factory()));
	}

	@Bean
	public CacheManager cacheManager(CacheProperties properties) {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager("topology", "symbols");
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(properties.getTopologyMaxSize())
				.expireAfterAccess(Duration.ofMinutes(properties.getTopologyExpireMinutes()))
				.recordStats());
		return cacheManager;
	}

	@Bean
	public Cache<String, List<Symbol>> symbolCache(CacheProperties properties) {
		return Caffeine.newBuilder()
				.maximumSize(properties.getSymbolCacheMaxSize())
				.expireAfterAccess(Duration.ofMinutes(properties.getSymbolCacheExpireMinutes()))
				.recordStats()
				.build();
	}
}
