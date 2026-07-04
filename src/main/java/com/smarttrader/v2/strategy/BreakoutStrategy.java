package com.smarttrader.v2.strategy;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Breakout Strategy, per V2_TECH_SPEC.md section 3 / SmartTrader_V2_Production_Spec.md section 5.
 *
 * Entry:  validBreakoutUp (price > resistance, strongCandle, volumeSpike) for LONG,
 *         or the symmetric breakdown (price < support, strongCandle, volumeSpike) for SHORT.
 * Stop:   ATR * BREAKOUT_RISK_ATR (1.2)
 * Target: ATR * BREAKOUT_REWARD_ATR (3.0)
 */
@Slf4j
@Component
public class BreakoutStrategy implements TradingStrategy {

    private static final String NAME = "BreakoutStrategy";

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        TradeDirection direction = resolveDirection(ctx);
        if (direction == TradeDirection.NONE) {
            return SignalResult.invalid(NAME);
        }

        double entry = ctx.price();
        double riskDistance = ctx.atr() * TradingConstants.BREAKOUT_RISK_ATR;
        double rewardDistance = ctx.atr() * TradingConstants.BREAKOUT_REWARD_ATR;

        double stop = direction == TradeDirection.LONG ? entry - riskDistance : entry + riskDistance;
        double target = direction == TradeDirection.LONG ? entry + rewardDistance : entry - rewardDistance;

        double riskReward = RiskRewardCalculator.riskReward(direction, entry, stop, target);
        boolean valid = riskReward >= TradingConstants.MIN_RISK_REWARD;

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
     * validBreakoutUp: price above resistance with a strong candle and volume spike -> LONG.
     * Symmetric breakdown below support -> SHORT.
     */
    private TradeDirection resolveDirection(AnalysisContext ctx) {
        if (!ctx.strongCandle() || !ctx.volumeSpike()) {
            return TradeDirection.NONE;
        }
        if (ctx.price() > ctx.nearestResistance()) {
            return TradeDirection.LONG;
        }
        if (ctx.price() < ctx.nearestSupport()) {
            return TradeDirection.SHORT;
        }
        return TradeDirection.NONE;
    }
}
