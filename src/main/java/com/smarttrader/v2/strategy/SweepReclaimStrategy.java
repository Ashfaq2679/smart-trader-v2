package com.smarttrader.v2.strategy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.smarttrader.v2.constants.PlaybookConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.OpportunitySweep;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;

import lombok.extern.slf4j.Slf4j;

/**
 * Sweep-and-Reclaim, per V2_TECH_SPEC_v2.5.md section 5.2: trade the trap, not the bait.
 *
 * Trigger: a LiquiditySweepDetected on a pool with density >= 50, reclaimed within 2 bars.
 * Direction: against the sweep (swept below EQL and reclaimed up -> long; swept above EQH
 * and reclaimed down -> short).
 *
 * relVolume >= 1.5 on the reclaim bar (section 5.2) isn't a field this codebase exposes
 * anywhere (no per-bar relative-volume metric beyond the boolean volumeSpike); volumeSpike
 * is used as the closest available confirmation of "meaningfully above-average volume".
 *
 * SL is placed 0.35 x ATR beyond the swept pool's level itself, not the exact wick extreme:
 * OpportunitySweep only records the pool level that was swept, not the price the wick
 * actually reached.
 */
@Slf4j
@Component
public class SweepReclaimStrategy implements TradingStrategy {

    private static final String NAME = "SweepReclaimStrategy";

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        List<OpportunitySweep> sweeps = ctx.recentSweeps();
        if (sweeps.isEmpty() || !ctx.volumeSpike()) {
            return SignalResult.invalid(NAME);
        }

        OpportunitySweep sweep = sweeps.get(0);
        if (sweep.getDensity() < PlaybookConstants.SWEEP_MIN_POOL_DENSITY || !sweep.isReclaimed()) {
            return SignalResult.invalid(NAME);
        }

        TradeDirection direction = "UP".equals(sweep.getSide()) ? TradeDirection.LONG : TradeDirection.SHORT;
        double level = sweep.getLevel().doubleValue();
        double entry = ctx.price();
        double stopOffset = ctx.atr() * PlaybookConstants.SWEEP_STOP_ATR_MULTIPLE;
        double stop = direction == TradeDirection.LONG ? level - stopOffset : level + stopOffset;
        double target = direction == TradeDirection.LONG ? ctx.nearestResistance() : ctx.nearestSupport();

        double riskReward = RiskRewardCalculator.riskReward(direction, entry, stop, target);
        boolean valid = riskReward >= PlaybookConstants.SWEEP_MIN_RISK_REWARD;

        SignalResult result = SignalResult.builder()
                .valid(valid)
                .strategyName(NAME)
                .direction(direction)
                .entry(entry)
                .stop(stop)
                .target(target)
                .riskReward(riskReward)
                .build();

        log.info("strategy={} valid={} direction={} entry={} stop={} target={} rr={} poolDensity={}",
                NAME, valid, direction, entry, stop, target, riskReward, sweep.getDensity());
        return result;
    }

    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.RANGE, MarketRegime.BREAKOUT);
    }
}
