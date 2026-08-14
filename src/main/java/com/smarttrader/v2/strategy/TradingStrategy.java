package com.smarttrader.v2.strategy;

import java.util.Set;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;

/**
 * Every strategy implements this. Strategies are pure functions of AnalysisContext:
 * no repository access, no side effects (per CLAUDE.md core engineering principles).
 *
 * applicableRegimes(), per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 2.6, declares
 * which MarketRegimes a strategy is a candidate for under the Playbook Matrix (section
 * 2). It's a default method returning an empty set so the existing v2.2 strategies
 * (Pullback/Breakout/Continuation, still routed by StrategySelector.select()) don't need
 * to implement it; only the new v2.5 strategies consumed via
 * StrategySelector.selectStrategies() declare theirs.
 */
public interface TradingStrategy {

    SignalResult evaluate(AnalysisContext ctx);

    default Set<MarketRegime> applicableRegimes() {
        return Set.of();
    }
}
