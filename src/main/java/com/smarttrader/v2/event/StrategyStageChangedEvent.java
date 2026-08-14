package com.smarttrader.v2.event;

import com.smarttrader.v2.model.StrategyStage;

/**
 * Fired whenever StrategyStateManager promotes or demotes a (strategy, symbol) pair,
 * per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.2.
 */
public class StrategyStageChangedEvent extends TradingEvent {

    public String strategyName;
    public StrategyStage oldStage;
    public StrategyStage newStage;
    public String reason;

    public StrategyStageChangedEvent() {
        super("strategy.StageChanged");
    }
}
