package com.smarttrader.v2.strategy;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.SignalResult;

/**
 * Every strategy implements this. Strategies are pure functions of AnalysisContext:
 * no repository access, no side effects (per CLAUDE.md core engineering principles).
 */
public interface TradingStrategy {

    SignalResult evaluate(AnalysisContext ctx);
}
