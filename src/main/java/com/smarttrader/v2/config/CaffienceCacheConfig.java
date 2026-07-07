package com.smarttrader.v2.config;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * Central Spring configuration for caching infrastructure.
 *
 * <p><b>Design Pattern: Factory Method</b> — Each {@code @Bean} method acts as a
 * factory method, encapsulating the creation logic for cache infrastructure.
 * Spring invokes these factory methods to produce and manage singleton instances
 * in the application context.</p>
 *
 * <p>Coinbase API clients are no longer created as application-wide singletons.
 * Per-user clients are managed by
 * {@link com.techcobber.smarttrader.v1.services.CoinbaseClientFactory}.</p>
 */
@Configuration
@EnableCaching
@Slf4j
public class CaffienceCacheConfig {
	
	@Value("${cache.expiration.minutes:60}")
	private long cacheExpirationMinutes; // Default expiration time in minutes

	@Bean
	Caffeine<Object, Object> caffeineConfig() {
		return Caffeine.newBuilder().expireAfterWrite(cacheExpirationMinutes, TimeUnit.MINUTES)
				.maximumSize(1000);
	}

	@Bean
	CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
		CaffeineCacheManager manager = new CaffeineCacheManager();
		manager.setCaffeine(caffeine);
		return manager;
	}
}
