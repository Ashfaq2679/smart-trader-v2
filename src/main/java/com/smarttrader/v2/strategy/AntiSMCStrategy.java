package com.smarttrader.v2.strategy;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Anti-SMC Module, per V2_TECH_SPEC_v2.5.md section 5.6: front-run Smart Money Concepts
 * (SMC) retail's Order Block (OB) / Fair Value Gap (FVG) entries and fade their failures.
 *
 * Genuinely unimplemented, not just data-starved: OB/FVG detection needs the raw OHLC
 * history around an impulsive move (>= 2 x ATR) and 3-bar gaps (>= 0.5 x ATR), which
 * requires scanning a candle series - something no strategy has access to by design
 * (TradingStrategy.evaluate(AnalysisContext) is a pure function of the current summarized
 * snapshot; "strategies never call repositories directly" per CLAUDE.md, and
 * AnalysisContext carries no raw candle list). Giving a strategy candle-history access
 * would be an architecture change bigger than "Phase 2: New Strategies" - flagging this
 * as the one new strategy this phase can wire in but not make real.
 */
@Slf4j
@Component
public class AntiSMCStrategy implements TradingStrategy {

    private static final String NAME = "AntiSMCStrategy";

    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        log.info("strategy={} valid=false reason=OB/FVG detection needs raw candle history not available to strategies", NAME);
        return SignalResult.invalid(NAME);
    }

    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.BREAKOUT, MarketRegime.CONTINUATION);
    }
}
