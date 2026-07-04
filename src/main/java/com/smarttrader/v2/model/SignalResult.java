package com.smarttrader.v2.model;

import lombok.Builder;

/**
 * Output of a TradingStrategy evaluation. valid=false means the entry condition
 * did not fire or the trade failed the minimum R:R filter (spec section 4/5).
 */
@Builder
public record SignalResult(
        boolean valid,
        String strategyName,
        TradeDirection direction,
        double entry,
        double stop,
        double target,
        double riskReward
) {

    public static SignalResult invalid(String strategyName) {
        return SignalResult.builder()
                .valid(false)
                .strategyName(strategyName)
                .direction(TradeDirection.NONE)
                .entry(0)
                .stop(0)
                .target(0)
                .riskReward(0)
                .build();
    }
}
