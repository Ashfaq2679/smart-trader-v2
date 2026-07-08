package com.smarttrader.v2.model;

import lombok.Builder;

/**
 * Final output of the decision pipeline (V2_TECH_SPEC_v1.1.md section 5, steps 4-7):
 * evaluate trade -> apply risk filters -> global risk check -> return signal.
 *
 * regimeConfidence carries the MarketRegimeResult confidence (section 2) through to the
 * final decision for logging (CLAUDE.md logging requirements call out "confidence").
 * effectiveRiskReward is the fee/slippage-adjusted R:R (section 4) that RiskEngine
 * actually filtered on, as opposed to signal.riskReward() which is the strategy's raw value.
 */
@Builder(toBuilder = true)
public record TradeDecision(
        boolean approved,
        MarketRegime regime,
        double regimeConfidence,
        SignalResult signal,
        double effectiveRiskReward,
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
