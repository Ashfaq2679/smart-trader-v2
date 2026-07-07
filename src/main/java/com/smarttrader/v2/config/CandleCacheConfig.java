package com.smarttrader.v2.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.CandleCacheKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache for per-(productId, granularity) candle series.
 *
 * Deliberately separate from CaffienceCacheConfig's generic Spring CacheManager: that
 * manager is unused elsewhere (no @Cacheable in the codebase) and its 60-minute
 * expireAfterWrite doesn't fit here — candle series should stay warm across normal
 * polling so CandleCacheService can keep fetching only the delta since the last
 * cached candle, not be evicted and re-fetched from scratch every hour. Uses raw
 * Caffeine (not Spring's Cache abstraction) for the same reason CoinbaseClientFactory
 * does: read-merge-write logic needs Cache.asMap().compute(...), which Spring's
 * @Cacheable model doesn't support.
 */
@Configuration
public class CandleCacheConfig {

    @Bean
    public Cache<CandleCacheKey, List<Candle>> candleCache(
            @Value("${cache.candles.expiration-hours:24}") long expirationHours,
            @Value("${cache.candles.max-size:500}") long maxSize) {
        return Caffeine.newBuilder()
                .expireAfterAccess(expirationHours, TimeUnit.HOURS)
                .maximumSize(maxSize)
                .build();
    }
}
