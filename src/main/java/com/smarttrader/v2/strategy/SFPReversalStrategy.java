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
 * Swing Failure Pattern (SFP) Reversal, per V2_TECH_SPEC_v2.5.md section 5.3.
 *
 * The spec's SFP definition ("bar takes out a prior swing extreme intrabar by >= 0.1 x
 * ATR but closes back beyond it, wick ratio >= 0.5") needs raw OHLC of the triggering
 * bar; AnalysisContext only carries the current summarized snapshot, not that. This
 * approximates the pattern with what IS on AnalysisContext: price sitting right at a
 * swing extreme (nearestSupport/nearestResistance), which is where an SFP wick would be
 * reaching from, combined with the confluence gate the spec requires (>= 1 of: CVD
 * divergence, funding extreme). AbsorptionDetected is an event, not an AnalysisContext
 * field, so it isn't checked here.
 */
@Slf4j
@Component
public class SFPReversalStrategy implements TradingStrategy {

    private static final String NAME = "SFPReversalStrategy";
    private static final double EDGE_PROXIMITY_ATR_MULTIPLE = 0.5;

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        double atr = ctx.atr();
        if (atr <= 0) {
            return SignalResult.invalid(NAME);
        }

        boolean atSupport = Math.abs(ctx.price() - ctx.nearestSupport()) <= atr * EDGE_PROXIMITY_ATR_MULTIPLE;
        boolean atResistance = Math.abs(ctx.price() - ctx.nearestResistance()) <= atr * EDGE_PROXIMITY_ATR_MULTIPLE;

        boolean fundingCrowdedShort = ctx.fundingPercentile30d() >= PlaybookConstants.SFP_FUNDING_EXTREME_HIGH_PERCENTILE;
        boolean fundingCrowdedLong = ctx.fundingPercentile30d() <= PlaybookConstants.SFP_FUNDING_EXTREME_LOW_PERCENTILE;

        TradeDirection direction;
        boolean hasConfluence;
        if (atSupport) {
            direction = TradeDirection.LONG;
            hasConfluence = ctx.cvdDivergence() || fundingCrowdedLong;
        } else if (atResistance) {
            direction = TradeDirection.SHORT;
            hasConfluence = ctx.cvdDivergence() || fundingCrowdedShort;
        } else {
            return SignalResult.invalid(NAME);
        }

        if (!hasConfluence) {
            return SignalResult.invalid(NAME);
        }

        double entry = ctx.price();
        double stop = direction == TradeDirection.LONG
                ? ctx.nearestSupport() - atr * TradingConstants.NEAR_ATR_MULTIPLIER
                : ctx.nearestResistance() + atr * TradingConstants.NEAR_ATR_MULTIPLIER;
        double riskDistance = Math.abs(entry - stop);
        double target = direction == TradeDirection.LONG
                ? entry + riskDistance * PlaybookConstants.SFP_TARGET_RISK_REWARD
                : entry - riskDistance * PlaybookConstants.SFP_TARGET_RISK_REWARD;

        double riskReward = RiskRewardCalculator.riskReward(direction, entry, stop, target);
        // Target is constructed as riskDistance * SFP_TARGET_RISK_REWARD, so riskReward should
        // come back to exactly that ratio; a small epsilon absorbs floating-point rounding
        // (e.g. 1.1999999999999982) so an intentionally-exact-minimum setup isn't rejected.
        boolean valid = riskReward >= PlaybookConstants.SFP_TARGET_RISK_REWARD - 1e-9;

        SignalResult result = SignalResult.builder()
                .valid(valid)
                .strategyName(NAME)
                .direction(direction)
                .entry(entry)
                .stop(stop)
                .target(target)
                .riskReward(riskReward)
                .build();

        log.info("strategy={} valid={} direction={} entry={} stop={} target={} rr={}",
                NAME, valid, direction, entry, stop, target, riskReward);
        return result;
    }

    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.PULLBACK, MarketRegime.RANGE);
    }
}
