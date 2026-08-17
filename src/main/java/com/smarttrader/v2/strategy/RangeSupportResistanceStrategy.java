package com.smarttrader.v2.strategy;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.strategy.range.RangeStructure;
import com.smarttrader.v2.strategy.range.RangeStructureDetector;
import com.smarttrader.v2.strategy.range.RangeSupportResistanceConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Range mean-reversion strategy: buys validated support, sells validated resistance while
 * inside a confirmed horizontal range. Per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL_WITH_RANGE_SR.md
 * §2.3.1.
 *
 * <p>Independent from {@link RangeHarvesterStrategy}. Feature-flagged via
 * {@link RangeSupportResistanceConfig#enabled()}; when disabled, {@link #evaluate} always
 * returns {@link SignalResult#invalid} - StrategySelector routing therefore stays byte-for-byte
 * identical to today's behaviour until the flag is flipped.
 *
 * <p>Signal name: {@code "RangeSupportResistance"} (spec line 942). The exit monitor keys
 * off that string so it can never close another strategy's position.
 *
 * <p><b>Regime gate:</b> the spec skeleton (§2.3.1 line 966) checks {@code ctx.regime() ==
 * RANGE}, but {@link AnalysisContext} does not expose a regime accessor - {@link StrategySelector}
 * enforces the regime→strategy mapping upstream (matching how {@link RangeHarvesterStrategy}
 * behaves today). The strategy therefore trusts its caller for the regime, as its peers do.
 */
@Slf4j
@Component
public class RangeSupportResistanceStrategy implements TradingStrategy {

    public static final String NAME = "RangeSupportResistance";

    private final RangeStructureDetector rangeDetector;
    private final RangeSupportResistanceConfig config;
    /**
     * One-shot re-entry block toggled by {@link com.smarttrader.v2.strategy.range.RangeBoundaryExitMonitor}
     * on a boundary break. Cleared only when {@link #evaluate} detects a newly validated
     * range (spec lines 1006-1019: "A simple return inside the old range is not sufficient").
     * Per-JVM/per-strategy-instance because {@link AnalysisContext} does not carry a symbol id.
     */
    private final AtomicBoolean rangeInvalidated = new AtomicBoolean(false);

    public RangeSupportResistanceStrategy(RangeStructureDetector rangeDetector,
                                          RangeSupportResistanceConfig config) {
        this.rangeDetector = rangeDetector;
        this.config = config;
    }

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        if (!config.enabled()) {
            return SignalResult.invalid(NAME);
        }

        RangeStructure range = rangeDetector.detect(ctx, config);

        if (!range.valid() || range.qualityScore() < config.minimumRangeQuality()) {
            return SignalResult.invalid(NAME);
        }

        // A newly validated range clears the boundary-break re-entry block, BUT the
        // very first evaluation that observes the new range still suppresses any signal:
        // spec lines 1006-1019 require re-validation before another entry, so a simple
        // return inside the still-valid range is insufficient. Emission resumes on the
        // subsequent evaluate() call.
        if (rangeInvalidated.getAndSet(false)) {
            return SignalResult.invalid(NAME);
        }

        double atr = ctx.atr();
        double price = ctx.price();

        if (nearSupport(price, range, atr) && supportRejectionConfirmed(ctx, range)) {
            SignalResult signal = buildLongSignal(ctx, range);
            if (signal.valid() && signal.riskReward() >= config.minimumRiskReward()) {
                return signal;
            }
        }

        if (nearResistance(price, range, atr) && resistanceRejectionConfirmed(ctx, range)) {
            SignalResult signal = buildShortSignal(ctx, range);
            if (signal.valid() && signal.riskReward() >= config.minimumRiskReward()) {
                return signal;
            }
        }

        return SignalResult.invalid(NAME);
    }

    private boolean nearSupport(double price, RangeStructure range, double atr) {
        double buffer = config.supportEntryBufferAtr() * atr;
        return price <= range.support().upper() + buffer;
    }

    private boolean nearResistance(double price, RangeStructure range, double atr) {
        double buffer = config.resistanceEntryBufferAtr() * atr;
        return price >= range.resistance().lower() - buffer;
    }

    /** Rejection = the current close is back above support.lower (wick in, close above). */
    private boolean supportRejectionConfirmed(AnalysisContext ctx, RangeStructure range) {
        return ctx.price() >= range.support().lower();
    }

    private boolean resistanceRejectionConfirmed(AnalysisContext ctx, RangeStructure range) {
        return ctx.price() <= range.resistance().upper();
    }

    private SignalResult buildLongSignal(AnalysisContext ctx, RangeStructure range) {
        double entry = ctx.price();
        double stop = range.support().lower() - config.stopBufferAtr() * ctx.atr();
        double target = range.resistance().midpoint();
        return buildSignal(TradeDirection.LONG, entry, stop, target);
    }

    private SignalResult buildShortSignal(AnalysisContext ctx, RangeStructure range) {
        double entry = ctx.price();
        double stop = range.resistance().upper() + config.stopBufferAtr() * ctx.atr();
        double target = range.support().midpoint();
        return buildSignal(TradeDirection.SHORT, entry, stop, target);
    }

    private SignalResult buildSignal(TradeDirection direction, double entry, double stop, double target) {
        double riskReward = RiskRewardCalculator.riskReward(direction, entry, stop, target);
        boolean valid = riskReward >= config.minimumRiskReward();
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

    /**
     * Called by {@link com.smarttrader.v2.strategy.range.RangeBoundaryExitMonitor} after
     * a boundary break to block immediate re-entry. Cleared automatically the next time
     * {@link #evaluate} detects a newly validated range (spec lines 1006-1019).
     */
    public void markRangeInvalidated() {
        rangeInvalidated.set(true);
    }

    public boolean isRangeInvalidated() {
        return rangeInvalidated.get();
    }

    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.RANGE);
    }
}
