package com.smarttrader.v2.engine;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.regime.MarketRegimeDetector;
import com.smarttrader.v2.risk.RiskEngine;
import com.smarttrader.v2.strategy.StrategySelector;
import com.smarttrader.v2.strategy.TradingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the full decision flow, per V2_TECH_SPEC.md section 5:
 *
 *   1. Build AnalysisContext (done upstream, passed in here)
 *   2. Detect MarketRegime
 *   3. Select Strategy
 *   4. Evaluate trade
 *   5. Apply R:R filter
 *   6. Return TradeDecision
 *
 * Strategies never call repositories directly; this engine only composes
 * MarketRegimeDetector, StrategySelector and RiskEngine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEngine {

    private final MarketRegimeDetector marketRegimeDetector;
    private final StrategySelector strategySelector;
    private final RiskEngine riskEngine;

    public TradeDecision decide(AnalysisContext ctx, double capital) {
        MarketRegime regime = marketRegimeDetector.detect(ctx);

        return strategySelector.select(regime)
                .map(strategy -> evaluateAndFilter(regime, strategy, ctx, capital))
                .orElseGet(() -> noStrategyDecision(regime));
    }

    private TradeDecision evaluateAndFilter(MarketRegime regime, TradingStrategy strategy,
                                             AnalysisContext ctx, double capital) {
        SignalResult signal = strategy.evaluate(ctx);
        TradeDecision decision = riskEngine.evaluate(regime, signal, capital);
        log.info("tradeEngine regime={} strategy={} approved={}", regime, signal.strategyName(), decision.approved());
        return decision;
    }

    private TradeDecision noStrategyDecision(MarketRegime regime) {
        log.info("tradeEngine regime={} approved=false reason=no strategy for regime", regime);
        return TradeDecision.rejected(regime, SignalResult.invalid("none"), "no strategy defined for regime " + regime);
    }
}
