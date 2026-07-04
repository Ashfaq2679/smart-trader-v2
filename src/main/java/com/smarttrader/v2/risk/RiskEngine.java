package com.smarttrader.v2.risk;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Applies the risk rules from V2_TECH_SPEC.md section 4:
 * - Minimum R:R = 2.0
 * - Risk per trade = 1% capital
 * - positionSize = riskCapital / (entry - stop)
 *
 * Never approves a trade without stop, target, position size and R:R
 * (SmartTrader_V2_Production_Spec.md risk rules).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEngine {

    /** Risk per trade = 1% of capital, per spec section 4. */
    public static final double DEFAULT_RISK_PERCENT = 0.01;

    private final PositionSizing positionSizing;

    public TradeDecision evaluate(MarketRegime regime, SignalResult signal, double capital) {
        return evaluate(regime, signal, capital, DEFAULT_RISK_PERCENT);
    }

    public TradeDecision evaluate(MarketRegime regime, SignalResult signal, double capital, double riskPercent) {
        if (!signal.valid()) {
            return reject(regime, signal, "strategy produced no valid signal");
        }
        if (signal.riskReward() < TradingConstants.MIN_RISK_REWARD) {
            return reject(regime, signal, "risk:reward %.2f below minimum %.2f"
                    .formatted(signal.riskReward(), TradingConstants.MIN_RISK_REWARD));
        }

        double positionSize = positionSizing.calculate(capital, riskPercent, signal.entry(), signal.stop());
        if (positionSize <= 0) {
            return reject(regime, signal, "invalid stop distance, cannot size position");
        }

        TradeDecision decision = TradeDecision.builder()
                .approved(true)
                .regime(regime)
                .signal(signal)
                .positionSize(positionSize)
                .reason("approved")
                .build();

        log.info("riskEngine regime={} strategy={} rr={} positionSize={} approved=true",
                regime, signal.strategyName(), signal.riskReward(), positionSize);
        return decision;
    }

    private TradeDecision reject(MarketRegime regime, SignalResult signal, String reason) {
        log.info("riskEngine regime={} strategy={} approved=false reason={}", regime, signal.strategyName(), reason);
        return TradeDecision.rejected(regime, signal, reason);
    }
}
