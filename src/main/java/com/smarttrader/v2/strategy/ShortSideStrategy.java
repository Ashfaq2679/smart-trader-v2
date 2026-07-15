package com.smarttrader.v2.strategy;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;

import lombok.extern.slf4j.Slf4j;

/**
 * Short-Side Engine, per V2_TECH_SPEC_v2.5.md section 6.2: down moves are 1.5-2x faster
 * than up moves (long liquidation cascades) - the system must never go quiet on a
 * downtrend just because it can't always execute it.
 *
 * Detection: TREND_DOWN + oiConfirmsDown + CVD trending down (cvdSlope5m < 0 stands in for
 * "CVD 20-bar low": no dedicated new-low check is exposed for CVD in the down direction,
 * only CVDCalculatorService.isNewHigh(), which isn't reusable here without inverting the
 * whole rolling-history API for one caller).
 *
 * Section 6.1: spot Coinbase Advanced Trade cannot short by default (venue.can-short).
 * When it can't, this always returns invalid: making the detected setup visible as a
 * "Siren-grade alert + defensive automation" is Phase 3's job (Opportunity Siren), not
 * something a SignalResult (which only models executable trades) can carry on its own.
 */
@Slf4j
@Component
public class ShortSideStrategy implements TradingStrategy {

    private static final String NAME = "ShortSideStrategy";

    private final boolean canShort;

    public ShortSideStrategy(@Value("${venue.can-short:false}") boolean canShort) {
        this.canShort = canShort;
    }

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        if (!isShortSetup(ctx)) {
            return SignalResult.invalid(NAME);
        }
        if (!canShort) {
            log.info("strategy={} valid=false reason=venue cannot short, detected TREND_DOWN setup not executable", NAME);
            return SignalResult.invalid(NAME);
        }

        double entry = ctx.price();
        double stop = entry + ctx.atr() * TradingConstants.BREAKOUT_RISK_ATR;
        double target = entry - ctx.atr() * TradingConstants.BREAKOUT_REWARD_ATR;

        double riskReward = RiskRewardCalculator.riskReward(TradeDirection.SHORT, entry, stop, target);
        boolean valid = riskReward >= TradingConstants.MIN_RISK_REWARD;

        SignalResult result = SignalResult.builder()
                .valid(valid)
                .strategyName(NAME)
                .direction(TradeDirection.SHORT)
                .entry(entry)
                .stop(stop)
                .target(target)
                .riskReward(riskReward)
                .build();

        log.info("strategy={} valid={} direction=SHORT entry={} stop={} target={} rr={}",
                NAME, valid, entry, stop, target, riskReward);
        return result;
    }

    private boolean isShortSetup(AnalysisContext ctx) {
        return ctx.trendDirection() == TrendDirection.DOWN
                && ctx.oiConfirmsDown()
                && ctx.cvdSlope5m() < 0;
    }

    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.PANIC, MarketRegime.PULLBACK);
    }
}
