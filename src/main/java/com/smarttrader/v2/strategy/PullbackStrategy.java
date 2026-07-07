package com.smarttrader.v2.strategy;

import com.smarttrader.v2.calc.RiskRewardCalculator;
import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pullback Strategy, per V2_TECH_SPEC_v1.1.md sections 1/3 (supersedes V2_TECH_SPEC.md section 3).
 *
 * Entry:  bullishLocation == true (uptrend, price near EMA50 or support), placed as a LIMIT
 *         order per SmartTrader_V2_Production_Spec.md section 7 ("use limit orders for pullbacks")
 * Stop:   support - ATR * 0.5
 * Target: resistance
 */
@Slf4j
@Component
public class PullbackStrategy implements TradingStrategy {

    private static final String NAME = "PullbackStrategy";

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        if (!isBullishLocation(ctx)) {
            return SignalResult.invalid(NAME);
        }

        double entry = ctx.price();
        double stop = ctx.nearestSupport() - ctx.atr() * TradingConstants.PULLBACK_STOP_ATR_BUFFER;
        double target = ctx.nearestResistance();
        double riskReward = RiskRewardCalculator.riskReward(TradeDirection.LONG, entry, stop, target);
        boolean valid = riskReward >= TradingConstants.MIN_RISK_REWARD;

        SignalResult result = SignalResult.builder()
                .valid(valid)
                .strategyName(NAME)
                .direction(TradeDirection.LONG)
                .entry(entry)
                .entryType(EntryType.LIMIT)
                .validityWindow(TradingConstants.adjustedValidityWindow(
                        TradingConstants.PULLBACK_VALIDITY_WINDOW, ctx.atrSpike()))
                .stop(stop)
                .target(target)
                .riskReward(riskReward)
                .build();

        log.info("strategy={} valid={} entry={} stop={} target={} rr={}", NAME, valid, entry, stop, target, riskReward);
        return result;
    }

    /**
     * bullishLocation: uptrend AND price sitting near EMA50 or support, i.e. a
     * favorable long-entry location within an established uptrend.
     */
    private boolean isBullishLocation(AnalysisContext ctx) {
        if (ctx.trendDirection() != TrendDirection.UP) {
            return false;
        }
        double atr = ctx.atr();
        boolean nearEma = Math.abs(ctx.price() - ctx.ema50()) <= atr * TradingConstants.NEAR_ATR_MULTIPLIER;
        boolean nearSupport = Math.abs(ctx.price() - ctx.nearestSupport()) <= atr * TradingConstants.NEAR_ATR_MULTIPLIER;
        return nearEma || nearSupport;
    }
}
