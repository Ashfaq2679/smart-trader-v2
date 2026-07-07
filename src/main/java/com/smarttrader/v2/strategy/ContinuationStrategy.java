package com.smarttrader.v2.strategy;

import com.smarttrader.v2.calc.RiskRewardCalculator;
import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Continuation Strategy, per V2_TECH_SPEC_v1.1.md sections 1/3 (supersedes V2_TECH_SPEC.md section 3).
 *
 * Entry:  breakoutContinuation == true (recentBreakout, price holds above EMA, tight consolidation),
 *         placed as a MARKET order: the spec's execution rules only call out market orders for
 *         breakouts and limit orders for pullbacks, so continuation (a post-breakout momentum
 *         entry) follows the breakout convention here — flag if a limit entry is preferred instead.
 * Stop:   the more conservative (lower) of EMA50 or consolidation low
 * Target: ATR extension (no prior measured move is available on AnalysisContext, so we use
 *         entry + ATR * CONTINUATION_TARGET_ATR as the extension target)
 */
@Slf4j
@Component
public class ContinuationStrategy implements TradingStrategy {

    private static final String NAME = "ContinuationStrategy";

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        if (!isBreakoutContinuation(ctx)) {
            return SignalResult.invalid(NAME);
        }

        double entry = ctx.price();
        double consolidationLow = entry * (1 - ctx.consolidationRangePercent());
        double stop = Math.min(ctx.ema50(), consolidationLow);
        double target = entry + ctx.atr() * TradingConstants.CONTINUATION_TARGET_ATR;

        double riskReward = RiskRewardCalculator.riskReward(TradeDirection.LONG, entry, stop, target);
        boolean valid = riskReward >= TradingConstants.MIN_RISK_REWARD;

        SignalResult result = SignalResult.builder()
                .valid(valid)
                .strategyName(NAME)
                .direction(TradeDirection.LONG)
                .entry(entry)
                .entryType(EntryType.MARKET)
                .validityWindow(TradingConstants.adjustedValidityWindow(
                        TradingConstants.CONTINUATION_VALIDITY_WINDOW, ctx.atrSpike()))
                .stop(stop)
                .target(target)
                .riskReward(riskReward)
                .build();

        log.info("strategy={} valid={} entry={} stop={} target={} rr={}", NAME, valid, entry, stop, target, riskReward);
        return result;
    }

    /**
     * breakoutContinuation: recentBreakout AND price holds above EMA AND consolidation range < 2%.
     */
    private boolean isBreakoutContinuation(AnalysisContext ctx) {
        return ctx.recentBreakout()
                && ctx.isAboveEMA()
                && ctx.consolidationRangePercent() < TradingConstants.CONTINUATION_CONSOLIDATION_THRESHOLD;
    }
}
