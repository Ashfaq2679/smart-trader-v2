package com.smarttrader.v2.engine;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.event.DomainEventPublisher;
import com.smarttrader.v2.event.RegimeDetectedEvent;
import com.smarttrader.v2.event.SignalGeneratedEvent;
import com.smarttrader.v2.integrity.DataIntegrityValidator;
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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the full decision flow, per V2_TECH_SPEC_v1.1.md section 5:
 *
 *   1. Build AnalysisContext (done upstream, passed in here)
 *   1.5. Reject stale data (section 10, guards the whole pipeline entry point)
 *   2. Detect MarketRegime (regime + confidence) -> publishes RegimeDetectedEvent
 *   3. Select Strategy
 *   4. Evaluate trade -> publishes SignalGeneratedEvent
 *   5. Apply Risk Filters (fee/slippage-adjusted R:R, position sizing)
 *   6. Global Risk Check (section 8 portfolio controls, via GlobalRiskCheck/PortfolioRiskService)
 *   7. Return Signal
 *
 * Strategies never call repositories directly; this engine only composes
 * MarketRegimeDetector, StrategySelector, RiskEngine and GlobalRiskCheck.
 *
 * correlationId ties every event published during one decide() call together (section 9);
 * callers that want to correlate the downstream OrderExecutionService/PositionService
 * calls with this decision should reuse the same correlationId there.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEngine {

    private final MarketRegimeDetector marketRegimeDetector;
    private final StrategySelector strategySelector;
    private final RiskEngine riskEngine;
    private final GlobalRiskCheck globalRiskCheck;
    private final DataIntegrityValidator dataIntegrityValidator;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;
    
    @PostConstruct
    public void init() {
		log.info("TradeEngine initialized with MarketRegimeDetector: {}, StrategySelector: {}, RiskEngine: {}, GlobalRiskCheck: {}, DataIntegrityValidator: {}, DomainEventPublisher: {}",
				marketRegimeDetector.getClass().getSimpleName(), strategySelector.getClass().getSimpleName(), riskEngine.getClass().getSimpleName(),	 globalRiskCheck.getClass().getSimpleName(), dataIntegrityValidator.getClass().getSimpleName(), eventPublisher.getClass().getSimpleName());
	}

    public TradeDecision decide(AnalysisContext ctx, String productId, double capital) {
        return decide(ctx, productId, capital, RiskEngine.DEFAULT_RISK_PERCENT,
                TradingConstants.DEFAULT_FEES, TradingConstants.DEFAULT_SLIPPAGE, UUID.randomUUID().toString());
    }

    public TradeDecision decide(AnalysisContext ctx, String productId, double capital,
                                 double riskPercent, double fees, double slippage) {
        return decide(ctx, productId, capital, riskPercent, fees, slippage, UUID.randomUUID().toString());
    }

    public TradeDecision decide(AnalysisContext ctx, String productId, double capital,
                                 double riskPercent, double fees, double slippage, String correlationId) {
        dataIntegrityValidator.rejectStaleData(ctx);

        MarketRegimeResult regimeResult = marketRegimeDetector.detect(ctx);
        MarketRegime regime = regimeResult.regime();
        eventPublisher.publish(RegimeDetectedEvent.of(correlationId, productId, regime, regimeResult.confidence()));

        TradeDecision decision = strategySelector.select(regime)
                .map(strategy -> evaluateAndFilter(regimeResult, strategy, ctx, productId, capital, riskPercent, fees, slippage, correlationId))
                .orElseGet(() -> noStrategyDecision(regimeResult));

        TradeDecision finalDecision = globalRiskCheck.apply(decision, productId, capital, correlationId, Instant.now(clock));
        log.info("tradeEngine correlationId={} regime={} confidence={} approved={}",
                correlationId, regime, regimeResult.confidence(), finalDecision.approved());
        return finalDecision;
    }

    private TradeDecision evaluateAndFilter(MarketRegimeResult regimeResult, TradingStrategy strategy, AnalysisContext ctx,
                                             String productId, double capital, double riskPercent, double fees,
                                             double slippage, String correlationId) {
        SignalResult signal = strategy.evaluate(ctx);
        eventPublisher.publish(SignalGeneratedEvent.of(correlationId, productId, signal));
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
