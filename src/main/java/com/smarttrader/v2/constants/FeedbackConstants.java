package com.smarttrader.v2.constants;

/**
 * Constants for the live feedback loops (V2_TECH_SPEC_v2.5.md section 12 /
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5), "validated: false" per section 12
 * until real sensitivity analysis, same as the other v2.5 subsystem constants classes.
 */
public final class FeedbackConstants {

    /** SlippageCalibrator: how many recent outcomes to weight per symbol. */
    public static final int SLIPPAGE_LOOKBACK_TRADES = 100;

    /** SlippageCalibrator: exponential weighting half-life, in fills. */
    public static final double SLIPPAGE_HALF_LIFE_FILLS = 100.0;

    /** SlippageCalibrator: realized/modeled ratio above which an alert + model update fires. */
    public static final double SLIPPAGE_ALERT_MULTIPLE = 2.0;

    /** ThresholdDriftEstimator: rolling ATR window used to build the percentile distribution. */
    public static final int THRESHOLD_ATR_PERIOD = 14;

    /** ThresholdDriftEstimator: fraction change vs current config that counts as "drift". */
    public static final double THRESHOLD_DRIFT_FRACTION = 0.5;

    /** ThresholdDriftEstimator: percentile of the ATR distribution tracked. */
    public static final int THRESHOLD_ATR_PERCENTILE = 90;

    /** MetaAllocator: rolling trade window used to rank strategies. */
    public static final int META_ALLOCATOR_ROLLING_TRADES = 60;

    /** MetaAllocator: risk multiplier for the top third of ranked strategies. */
    public static final double META_ALLOCATOR_TOP_TIER_MULTIPLIER = 1.2;

    /** MetaAllocator: risk multiplier for the middle third of ranked strategies. */
    public static final double META_ALLOCATOR_MID_TIER_MULTIPLIER = 1.0;

    /** MetaAllocator: risk multiplier for the bottom third of ranked strategies. */
    public static final double META_ALLOCATOR_BOTTOM_TIER_MULTIPLIER = 0.6;

    private FeedbackConstants() {
    }
}
