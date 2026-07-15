package com.smarttrader.v2.liquidity;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smarttrader.v2.constants.LiquidityConstants;
import com.smarttrader.v2.model.LiquidityPool;

/**
 * Per-symbol liquidity pool cache, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md section
 * 1A.1 ("Caffeine cache (1-hour TTL per symbol)"). A dedicated bean (not a shared generic
 * cache) so its eviction policy is independently tunable from any other cache.
 */
@Configuration
public class LiquidityCacheConfig {

    @Bean
    public Cache<String, List<LiquidityPool>> liquidityPoolCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(LiquidityConstants.CACHE_TTL_HOURS, TimeUnit.HOURS)
                .maximumSize(500)
                .build();
    }
}
