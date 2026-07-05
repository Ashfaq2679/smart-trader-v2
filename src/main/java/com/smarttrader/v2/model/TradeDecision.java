package com.smarttrader.v2.model;

import lombok.Builder;

/**
 * Final output of the decision pipeline (V2_TECH_SPEC.md section 5, steps 4-6):
 * evaluate trade -> apply R:R filter -> return decision.
 */
@Builder
public record TradeDecision(
        boolean approved,
        MarketRegime regime,
        SignalResult signal,
        double positionSize,
        String reason
) {

    public static TradeDecision rejected(MarketRegime regime, SignalResult signal, String reason) {
        return TradeDecision.builder()
                .approved(false)
                .regime(regime)
                .signal(signal)
                .positionSize(0)
                .reason(reason)
                .build();
    }
}
