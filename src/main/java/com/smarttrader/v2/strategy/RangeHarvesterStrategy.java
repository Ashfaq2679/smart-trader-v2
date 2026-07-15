package com.smarttrader.v2.strategy;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.smarttrader.v2.constants.PlaybookConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;

import lombok.extern.slf4j.Slf4j;

/**
 * Range Harvester, per V2_TECH_SPEC_v2.5.md section 5.5: buy the swept/touched dip at
 * range low, sell the swept/touched top at range high - the sideways-market cash machine
 * that replaces v2.2's timid Range-Fade.
 *
 * "Sell the top" needs a short, which section 6.1 gates behind venue.can-short (false by
 * default for spot Coinbase Advanced Trade): when shorting isn't enabled, a top-of-range
 * touch produces an invalid signal rather than an unexecutable SHORT (Phase 3's
 * Opportunity Siren is where that becomes a "Siren + flatten longs" alert instead).
 *
 * Round-trip caps (max 2 per side per session) and the 20-trade win-rate floor (section
 * 8) need trade-history state a stateless, ctx-only strategy doesn't have; those belong
 * with StrategyStateManager (Phase 4), not here.
 */
@Slf4j
@Component
public class RangeHarvesterStrategy implements TradingStrategy {

    private static final String NAME = "RangeHarvesterStrategy";

    private final boolean canShort;

    public RangeHarvesterStrategy(@Value("${venue.can-short:false}") boolean canShort) {
        this.canShort = canShort;
    }

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        double atr = ctx.atr();
        if (atr <= 0 || ctx.cascadeActive()) {
            return SignalResult.invalid(NAME);
        }

        double bandWidth = ctx.nearestResistance() - ctx.nearestSupport();
        if (bandWidth < PlaybookConstants.RANGE_MIN_BAND_WIDTH_ATR_MULTIPLE * atr) {
            return SignalResult.invalid(NAME);
        }

        double edgeProximity = atr * PlaybookConstants.RANGE_HARVESTER_EDGE_PROXIMITY_ATR_MULTIPLE;
        boolean atLow = ctx.price() <= ctx.nearestSupport() + edgeProximity;
        boolean atHigh = ctx.price() >= ctx.nearestResistance() - edgeProximity;

        if (atLow) {
            return buyTheDip(ctx);
        }
        if (atHigh) {
            return sellTheTop(ctx);
        }
        return SignalResult.invalid(NAME);
    }

    private SignalResult buyTheDip(AnalysisContext ctx) {
        double entry = ctx.price();
        double stop = ctx.nearestSupport() - ctx.atr() * PlaybookConstants.RANGE_HARVESTER_STOP_ATR_MULTIPLE;
        double target = (ctx.nearestSupport() + ctx.nearestResistance()) / 2.0; // TP1: mid-range
        return buildSignal(ctx, TradeDirection.LONG, entry, stop, target);
    }

    private SignalResult sellTheTop(AnalysisContext ctx) {
        if (!canShort) {
            log.info("strategy={} valid=false reason=venue cannot short, sell-the-top not executable", NAME);
            return SignalResult.invalid(NAME);
        }
        double entry = ctx.price();
        double stop = ctx.nearestResistance() + ctx.atr() * PlaybookConstants.RANGE_HARVESTER_STOP_ATR_MULTIPLE;
        double target = (ctx.nearestSupport() + ctx.nearestResistance()) / 2.0;
        return buildSignal(ctx, TradeDirection.SHORT, entry, stop, target);
    }

    private SignalResult buildSignal(AnalysisContext ctx, TradeDirection direction, double entry, double stop, double target) {
        double riskReward = RiskRewardCalculator.riskReward(direction, entry, stop, target);
        boolean valid = riskReward >= PlaybookConstants.RANGE_HARVESTER_MIN_RISK_REWARD;

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
        return Set.of(MarketRegime.RANGE);
    }
}
