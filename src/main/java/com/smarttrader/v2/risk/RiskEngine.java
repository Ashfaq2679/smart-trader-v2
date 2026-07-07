package com.smarttrader.v2.risk;

import com.smarttrader.v2.calc.RiskRewardCalculator;
import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Applies the risk rules from V2_TECH_SPEC_v1.1.md section 4 (decision flow step 5,
 * "Apply Risk Filters"):
 * - effectiveReward = target - entry - fees - slippage
 * - effectiveRisk   = entry - stop + fees + slippage
 * - Minimum R:R = 2.0 (on the effective, cost-adjusted ratio, not the raw signal R:R)
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
        return evaluate(regime, signal, capital, riskPercent,
                TradingConstants.DEFAULT_FEES, TradingConstants.DEFAULT_SLIPPAGE, 0.0);
    }

    public TradeDecision evaluate(MarketRegime regime, SignalResult signal, double capital, double riskPercent,
                                   double fees, double slippage, double regimeConfidence) {
        if (!signal.valid()) {
            return reject(regime, regimeConfidence, signal, 0, "strategy produced no valid signal");
        }

        double effectiveRiskReward = RiskRewardCalculator.effectiveRiskReward(
                signal.direction(), signal.entry(), signal.stop(), signal.target(), fees, slippage);
        if (effectiveRiskReward < TradingConstants.MIN_RISK_REWARD) {
            return reject(regime, regimeConfidence, signal, effectiveRiskReward,
                    "effective risk:reward %.2f below minimum %.2f"
                            .formatted(effectiveRiskReward, TradingConstants.MIN_RISK_REWARD));
        }

        double positionSize = positionSizing.calculate(capital, riskPercent, signal.entry(), signal.stop());
        if (positionSize <= 0) {
            return reject(regime, regimeConfidence, signal, effectiveRiskReward,
                    "invalid stop distance, cannot size position");
        }

        TradeDecision decision = TradeDecision.builder()
                .approved(true)
                .regime(regime)
                .regimeConfidence(regimeConfidence)
                .signal(signal)
                .effectiveRiskReward(effectiveRiskReward)
                .positionSize(positionSize)
                .reason("approved")
                .build();

        log.info("riskEngine regime={} confidence={} strategy={} effectiveRr={} positionSize={} approved=true",
                regime, regimeConfidence, signal.strategyName(), effectiveRiskReward, positionSize);
        return decision;
    }

    private TradeDecision reject(MarketRegime regime, double regimeConfidence, SignalResult signal,
                                  double effectiveRiskReward, String reason) {
        log.info("riskEngine regime={} confidence={} strategy={} approved=false reason={}",
                regime, regimeConfidence, signal.strategyName(), reason);
        return TradeDecision.builder()
                .approved(false)
                .regime(regime)
                .regimeConfidence(regimeConfidence)
                .signal(signal)
                .effectiveRiskReward(effectiveRiskReward)
                .positionSize(0)
                .reason(reason)
                .build();
    }
}
