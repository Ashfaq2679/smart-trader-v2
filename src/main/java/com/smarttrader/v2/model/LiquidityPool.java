package com.smarttrader.v2.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

/**
 * A liquidity pool (resting stop cluster), per V2_TECH_SPEC_v2.5.md section 3
 * ("Liquidity Map"). Mutable (density/touches update as new fractals/sweeps are observed),
 * unlike this codebase's immutable pipeline records - it's a tracked entity, not a
 * point-in-time calculation result.
 *
 * expiresAt backs the MongoDB TTL index (5 days, per the plan); expiresAtNs is kept
 * alongside it since the rest of the v2.5 liquidity/positioning code operates on
 * nanosecond longs (System.nanoTime()-style), not Instant.
 */
@Data
@Builder
@Document("liquidity_pools")
public class LiquidityPool {

    @Id
    private String id;

    @Indexed
    private String symbol;

    private BigDecimal level;

    private PoolType type;

    /** 0-100 normalized score: touches x volume-at-level x age decay (lambda = 0.8/day). */
    private float density;

    private int touches;

    private BigDecimal volume;

    private long lastTouchedNs;

    private long createdAtNs;

    private long expiresAtNs;

    /** TTL index: MongoDB expires the document when this timestamp is reached. */
    @Indexed(name = "liquidity_pool_ttl_idx", expireAfterSeconds = 0)
    private Instant expiresAt;
}
