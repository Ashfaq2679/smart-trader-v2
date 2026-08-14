package com.smarttrader.v2.constants;

/**
 * Constants for the Liquidity Map (V2_TECH_SPEC_v2.5.md section 3), kept separate from
 * TradingConstants since they're a distinct v2.5 subsystem with their own tunable priors -
 * all "validated: false" per section 12 until Stage-1 sensitivity analysis covers them.
 */
public final class LiquidityConstants {

    /** EQH/EQL clustering distance: extremes within 0.15 * ATR of each other are "equal". */
    public static final double EQH_EQL_ATR_THRESHOLD = 0.15;

    /** Density age decay: density *= lambda per day since the pool was created. */
    public static final double DENSITY_DECAY_LAMBDA_PER_DAY = 0.8;

    /** Pool TTL, per section 3 / Phase 0 Mongo schema: "liquidity_pools (TTL: 5 days)". */
    public static final int POOL_TTL_DAYS = 5;

    /** In-process cache freshness for a symbol's liquidity map, per Phase 1A.1. */
    public static final long CACHE_TTL_HOURS = 1;

    private LiquidityConstants() {
    }
}
