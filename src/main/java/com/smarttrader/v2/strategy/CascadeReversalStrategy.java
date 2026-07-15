package com.smarttrader.v2.strategy;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.smarttrader.v2.constants.PlaybookConstants;
import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;

import lombok.extern.slf4j.Slf4j;

/**
 * Cascade-Reversal, per V2_TECH_SPEC_v2.5.md section 6.3: the orderly pullback after a
 * liquidation cascade is one of the best long entries that exists - blanket NEWS_SHOCK
 * avoidance donates it to whales.
 *
 * Only the "Reversal" leg is implemented (buying the flush's aftermath). The "Ride" leg
 * (shorting the first pullback of a cascade that began from SQUEEZE_LONG) needs to know
 * which regime the cascade started FROM - a historical fact across multiple prior
 * AnalysisContext snapshots, not something derivable from the current one. That's
 * regime-history/state tracking, which belongs with StrategyStateManager (Phase 4), not
 * a stateless strategy.
 *
 * AbsorptionDetected(BID) is an event, not an AnalysisContext field; cvdSlope5m turning
 * positive (buying pressure returning) stands in for it. "SFP of the flush low" similarly
 * needs raw OHLC (see AntiSMCStrategy's javadoc for why that's out of reach here) -
 * approximated as price having reclaimed back above the support level the flush broke.
 *
 * Section 6.3's "Size x 0.5" isn't enforceable from a SignalResult: position sizing
 * happens downstream in RiskEngine/PositionSizing, which this strategy has no way to
 * influence per-signal. Flagging as a known gap pending a per-strategy risk multiplier.
 */
@Slf4j
@Component
public class CascadeReversalStrategy implements TradingStrategy {

    private static final String NAME = "CascadeReversalStrategy";

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        if (ctx.cascadeActive()) {
            // "During cascade: no entries" - unchanged.
            return SignalResult.invalid(NAME);
        }
        if (!isReversalConfirmed(ctx)) {
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
                .stop(stop)
                .target(target)
                .riskReward(riskReward)
                .build();

        log.info("strategy={} valid={} direction=LONG entry={} stop={} target={} rr={}",
                NAME, valid, entry, stop, target, riskReward);
        return result;
    }

    private boolean isReversalConfirmed(AnalysisContext ctx) {
        boolean oiStabilized = Math.abs(ctx.oiChange1h()) < PlaybookConstants.CASCADE_OI_STABILIZATION_THRESHOLD;
        boolean absorptionProxy = ctx.cvdSlope5m() > 0;
        boolean reclaimedFlushLow = ctx.price() > ctx.nearestSupport();
        boolean fundingReset = ctx.fundingPercentile30d() < PlaybookConstants.CASCADE_FUNDING_RESET_PERCENTILE;

        return oiStabilized && absorptionProxy && reclaimedFlushLow && fundingReset;
    }

    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.NEWS_SHOCK, MarketRegime.PANIC);
    }
}
