package com.smarttrader.v2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Enables index resolution (including TTL indexes declared via @Indexed(expireAfterSeconds=...),
 * e.g. LiquidityPool.expiresAt) for the v2.5 collections, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md section 0.5.
 *
 * MongoMappingContext is already auto-configured by spring-boot-starter-data-mongodb;
 * this just exposes an IndexResolver over it. Actual index creation at startup is
 * controlled by spring.data.mongodb.auto-index-creation (see application.yml).
 */
@Configuration
public class MongoDbConfig {

    @Bean
    public IndexResolver mongoIndexResolver(MongoMappingContext mongoMappingContext) {
        return new MongoPersistentEntityIndexResolver(mongoMappingContext);
    }
}
