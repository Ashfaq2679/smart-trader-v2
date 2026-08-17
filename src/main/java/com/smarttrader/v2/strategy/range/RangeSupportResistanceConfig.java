package com.smarttrader.v2.strategy.range;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature-flagged config for {@link com.smarttrader.v2.strategy.RangeSupportResistanceStrategy}.
 * YAML shape per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL_WITH_RANGE_SR.md §2.3.1 lines 776-795.
 *
 * <p>Defaults match the spec so instantiating this class without an application.yml block
 * yields the documented defaults, and {@code enabled=false} preserves existing RANGE
 * routing (§2.3.1 lines 1041-1064 critical invariant).
 */
@ConfigurationProperties(prefix = "smart-trader.v2_5.strategies.range-support-resistance")
public record RangeSupportResistanceConfig(
        boolean enabled,
        int minimumTouches,
        int preferredTouches,
        double minimumWidthAtr,
        double boundaryToleranceAtr,
        double minimumRangeQuality,
        double minimumEntryConfidence,
        double minimumRiskReward,
        double supportEntryBufferAtr,
        double resistanceEntryBufferAtr,
        double stopBufferAtr,
        double breakoutBufferAtr,
        double emergencyBreakBufferAtr,
        int maximumTradesPerSidePerSession
) {

    public RangeSupportResistanceConfig {
        if (minimumTouches == 0) minimumTouches = 2;
        if (preferredTouches == 0) preferredTouches = 3;
        if (minimumWidthAtr == 0) minimumWidthAtr = 2.0;
        if (boundaryToleranceAtr == 0) boundaryToleranceAtr = 0.15;
        if (minimumRangeQuality == 0) minimumRangeQuality = 70;
        if (minimumEntryConfidence == 0) minimumEntryConfidence = 70;
        if (minimumRiskReward == 0) minimumRiskReward = 1.5;
        if (supportEntryBufferAtr == 0) supportEntryBufferAtr = 0.15;
        if (resistanceEntryBufferAtr == 0) resistanceEntryBufferAtr = 0.15;
        if (stopBufferAtr == 0) stopBufferAtr = 0.10;
        if (breakoutBufferAtr == 0) breakoutBufferAtr = 0.10;
        if (emergencyBreakBufferAtr == 0) emergencyBreakBufferAtr = 0.25;
        if (maximumTradesPerSidePerSession == 0) maximumTradesPerSidePerSession = 2;
    }

    /** Convenience factory for the "feature disabled" default used by tests / non-Spring wiring. */
    public static RangeSupportResistanceConfig disabled() {
        return new RangeSupportResistanceConfig(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Convenience factory that enables the feature with all spec defaults. */
    public static RangeSupportResistanceConfig enabledDefaults() {
        return new RangeSupportResistanceConfig(true, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
