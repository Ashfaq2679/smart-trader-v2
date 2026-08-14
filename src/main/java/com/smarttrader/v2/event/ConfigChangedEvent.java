package com.smarttrader.v2.event;

/**
 * Fired whenever a Phase 5 feedback job (SlippageCalibrator, ThresholdDriftEstimator)
 * updates a tunable config value in response to detected real-world drift, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5.
 */
public class ConfigChangedEvent extends TradingEvent {

    public String configKey;
    public String oldValue;
    public String newValue;

    public ConfigChangedEvent() {
        super("feedback.ConfigChanged");
    }
}
