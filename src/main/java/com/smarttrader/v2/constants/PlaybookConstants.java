package com.smarttrader.v2.constants;

/**
 * Constants for v2.5 regime detection (V2_TECH_SPEC_v2.5.md section 2, Playbook Matrix)
 * and the new strategies (section 5), per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 2.
 * All "validated: false" per section 12 until Stage-1 sensitivity analysis covers them.
 */
public final class PlaybookConstants {

    // --- Regime detection ---

    /** SQUEEZE_LONG/SHORT: funding percentile crowded thresholds, section 2. */
    public static final int SQUEEZE_LONG_FUNDING_PERCENTILE = 90;
    public static final int SQUEEZE_SHORT_FUNDING_PERCENTILE = 10;
    /** SQUEEZE_LONG/SHORT: required OI growth, section 2 ("oiChange24h > +15%"). */
    public static final double SQUEEZE_OI_CHANGE_THRESHOLD = 0.15;

    /** RANGE: "bands wide, no trend, ADX < 25" - trendStrength stands in for ADX (no real ADX computed). */
    public static final double RANGE_TREND_STRENGTH_THRESHOLD = 0.25;
    /** RANGE: band width lower bound, section 5.5 ("band width >= 2 x ATR"). */
    public static final double RANGE_MIN_BAND_WIDTH_ATR_MULTIPLE = 2.0;
    /**
     * RANGE: band width upper bound. Not in the spec (which only gives a lower bound) -
     * added so a symbol with no real nearby support/resistance (support/resistance far
     * apart, effectively undefined structure) doesn't get misclassified as a tight
     * trading range just because trendStrength happens to be low at that instant.
     */
    public static final double RANGE_MAX_BAND_WIDTH_ATR_MULTIPLE = 10.0;

    // --- Sweep-and-Reclaim, section 5.2 ---
    public static final float SWEEP_MIN_POOL_DENSITY = 50f;
    public static final double SWEEP_STOP_ATR_MULTIPLE = 0.35;
    public static final double SWEEP_MIN_RISK_REWARD = 1.5;

    // --- SFP Reversal, section 5.3 ---
    public static final double SFP_TARGET_RISK_REWARD = 1.2;
    public static final int SFP_FUNDING_EXTREME_HIGH_PERCENTILE = 90;
    public static final int SFP_FUNDING_EXTREME_LOW_PERCENTILE = 10;

    // --- Range Harvester, section 5.5 ---
    public static final double RANGE_HARVESTER_EDGE_PROXIMITY_ATR_MULTIPLE = 0.3;
    public static final double RANGE_HARVESTER_STOP_ATR_MULTIPLE = 0.7;
    public static final double RANGE_HARVESTER_MIN_RISK_REWARD = 1.2;

    // --- Short-Side Engine / Cascade-Reversal, section 6 ---
    /** Section 6.1: spot Coinbase Advanced Trade cannot short; must be explicitly enabled. */
    public static final boolean DEFAULT_VENUE_CAN_SHORT = false;
    /** Section 6.3: reversal-after-cascade OI stabilization gate ("|delta 15m| < 1%"). */
    public static final double CASCADE_OI_STABILIZATION_THRESHOLD = 0.01;
    /** Section 6.3: reversal-after-cascade funding reset gate. */
    public static final int CASCADE_FUNDING_RESET_PERCENTILE = 50;

    private PlaybookConstants() {
    }
}
