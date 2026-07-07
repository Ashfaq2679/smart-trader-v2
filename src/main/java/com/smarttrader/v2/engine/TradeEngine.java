package com.smarttrader.v2.engine;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.MarketRegimeResult;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.regime.MarketRegimeDetector;
import com.smarttrader.v2.risk.GlobalRiskCheck;
import com.smarttrader.v2.risk.RiskEngine;
import com.smarttrader.v2.strategy.StrategySelector;
import com.smarttrader.v2.strategy.TradingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the full decision flow, per V2_TECH_SPEC_v1.1.md section 5:
 *
 *   1. Build AnalysisContext (done upstream, passed in here)
 *   2. Detect MarketRegime (regime + confidence)
 *   3. Select Strategy
 *   4. Evaluate trade
 *   5. Apply Risk Filters (fee/slippage-adjusted R:R, position sizing)
 *   6. Global Risk Check (portfolio-level; currently a pass-through, see GlobalRiskCheck)
 *   7. Return Signal
 *
 * Strategies never call repositories directly; this engine only composes
 * MarketRegimeDetector, StrategySelector, RiskEngine and GlobalRiskCheck.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEngine {

    private final MarketRegimeDetector marketRegimeDetector;
    private final StrategySelector strategySelector;
    private final RiskEngine riskEngine;
    private final GlobalRiskCheck globalRiskCheck;

    public TradeDecision decide(AnalysisContext ctx, double capital) {
        return decide(ctx, capital, RiskEngine.DEFAULT_RISK_PERCENT,
                TradingConstants.DEFAULT_FEES, TradingConstants.DEFAULT_SLIPPAGE);
    }

    public TradeDecision decide(AnalysisContext ctx, double capital, double riskPercent, double fees, double slippage) {
        MarketRegimeResult regimeResult = marketRegimeDetector.detect(ctx);
        MarketRegime regime = regimeResult.regime();

        TradeDecision decision = strategySelector.select(regime)
                .map(strategy -> evaluateAndFilter(regimeResult, strategy, ctx, capital, riskPercent, fees, slippage))
                .orElseGet(() -> noStrategyDecision(regimeResult));

        TradeDecision finalDecision = globalRiskCheck.apply(decision);
        log.info("tradeEngine regime={} confidence={} approved={}", regime, regimeResult.confidence(), finalDecision.approved());
        return finalDecision;
    }

    private TradeDecision evaluateAndFilter(MarketRegimeResult regimeResult, TradingStrategy strategy, AnalysisContext ctx,
                                             double capital, double riskPercent, double fees, double slippage) {
        SignalResult signal = strategy.evaluate(ctx);
        return riskEngine.evaluate(regimeResult.regime(), signal, capital, riskPercent, fees, slippage, regimeResult.confidence());
    }

    private TradeDecision noStrategyDecision(MarketRegimeResult regimeResult) {
        log.info("tradeEngine regime={} approved=false reason=no strategy for regime", regimeResult.regime());
        return TradeDecision.builder()
                .approved(false)
                .regime(regimeResult.regime())
                .regimeConfidence(regimeResult.confidence())
                .signal(SignalResult.invalid("none"))
                .reason("no strategy defined for regime " + regimeResult.regime())
                .build();
    }
}
