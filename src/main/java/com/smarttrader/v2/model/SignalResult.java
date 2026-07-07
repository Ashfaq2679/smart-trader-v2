package com.smarttrader.v2.model;

import lombok.Builder;

import java.time.Duration;

/**
 * Output of a TradingStrategy evaluation. valid=false means the entry condition
 * did not fire or the trade failed the minimum R:R filter (spec section 4/5).
 *
 * entryType and validityWindow are required per V2_TECH_SPEC_v1.1.md section 3:
 * every strategy must define how its entry should be placed (MARKET/LIMIT) and how
 * long the signal remains actionable before it must be re-evaluated.
 */
@Builder
public record SignalResult(
        boolean valid,
        String strategyName,
        TradeDirection direction,
        double entry,
        EntryType entryType,
        Duration validityWindow,
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
                .entryType(null)
                .validityWindow(Duration.ZERO)
                .stop(0)
                .target(0)
                .riskReward(0)
                .build();
    }
}
