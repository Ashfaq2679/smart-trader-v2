package com.smarttrader.v2.constants;

/**
 * Constants for Crowd Positioning & Order Flow (V2_TECH_SPEC_v2.5.md section 4),
 * per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 1B. Kept separate from
 * TradingConstants/LiquidityConstants since this is its own v2.5 subsystem - all
 * "validated: false" per section 12 until Stage-1 sensitivity analysis covers them.
 */
public final class PositioningConstants {

    /** CVD slope window, per section 1: "slope = linear regression over 20 bars". */
    public static final int CVD_SLOPE_WINDOW = 20;
    /** Rolling CVD history retained for slope/divergence lookups. */
    public static final int CVD_MAX_HISTORY = 100;
    /** "new 20-bar high" lookback for CVD divergence (section 4). */
    public static final int CVD_DIVERGENCE_LOOKBACK = 20;

    /** Funding polled every 8 hours, per Phase 1B.2. */
    public static final long FUNDING_POLL_INTERVAL_MS = 28_800_000L;
    /** 3 polls/day x 30 days: bounds the 30-day funding distribution. */
    public static final int FUNDING_HISTORY_MAX_SAMPLES = 90;
    /** Funding percentile is cached for 1h per symbol, per Phase 1B.2. */
    public static final long FUNDING_PERCENTILE_CACHE_TTL_MINUTES = 60;

    /** OI polled hourly, per Phase 1B.3. */
    public static final long OI_POLL_INTERVAL_MS = 3_600_000L;
    /** Bounds retained OI samples to roughly 48h of hourly polling. */
    public static final int OI_MAX_HISTORY = 48;

    /** Absorption detection, section 4: ">= 2.5x relative volume sell-taker flow". */
    public static final double ABSORPTION_RELATIVE_VOLUME_THRESHOLD = 2.5;
    /** Absorption detection, section 4: "price declines < 0.25 x ATR". */
    public static final double ABSORPTION_MAX_PRICE_DECLINE_ATR = 0.25;

    private PositioningConstants() {
    }
}
