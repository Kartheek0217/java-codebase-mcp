package com.mcp.config;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mcp.entity.Symbol;
import com.mcp.properties.CacheProperties;

@Configuration
public class CacheConfig {

	@Bean
	@SuppressWarnings("unchecked")
	public CacheManager cacheManager(CacheProperties properties, Cache<String, List<Symbol>> symbolCache) {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager("topology");
		cacheManager.registerCustomCache("symbols", (Cache<Object, Object>) (Cache<?, ?>) symbolCache);
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
