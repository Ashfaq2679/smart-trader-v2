package com.smarttrader.v2.engine;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.regime.MarketRegimeDetector;
import com.smarttrader.v2.risk.RiskEngine;
import com.smarttrader.v2.strategy.StrategySelector;
import com.smarttrader.v2.strategy.TradingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeEngineTest {

    @Mock
    private MarketRegimeDetector marketRegimeDetector;
    @Mock
    private StrategySelector strategySelector;
    @Mock
    private RiskEngine riskEngine;
    @Mock
    private TradingStrategy strategy;

    private TradeEngine tradeEngine;

    @BeforeEach
    void setUp() {
        tradeEngine = new TradeEngine(marketRegimeDetector, strategySelector, riskEngine);
    }

    private AnalysisContext anyContext() {
        return AnalysisContext.builder().build();
    }

    @Test
    void bullish_pullbackRegimeRoutesThroughStrategyAndRiskEngine() {
        AnalysisContext ctx = anyContext();
        SignalResult signal = SignalResult.builder().valid(true).strategyName("PullbackStrategy")
                .direction(TradeDirection.LONG).entry(100).stop(95).target(110).riskReward(2.0).build();
        TradeDecision expected = TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK)
                .signal(signal).positionSize(10).reason("approved").build();

        when(marketRegimeDetector.detect(ctx)).thenReturn(MarketRegime.PULLBACK);
        when(strategySelector.select(MarketRegime.PULLBACK)).thenReturn(Optional.of(strategy));
        when(strategy.evaluate(ctx)).thenReturn(signal);
        when(riskEngine.evaluate(MarketRegime.PULLBACK, signal, 10_000)).thenReturn(expected);

        TradeDecision decision = tradeEngine.decide(ctx, 10_000);

        assertThat(decision).isEqualTo(expected);
    }

    @Test
    void bearish_panicRegimeIsRejectedWithoutInvokingRiskEngine() {
        AnalysisContext ctx = anyContext();
        when(marketRegimeDetector.detect(ctx)).thenReturn(MarketRegime.PANIC);
        when(strategySelector.select(MarketRegime.PANIC)).thenReturn(Optional.empty());

        TradeDecision decision = tradeEngine.decide(ctx, 10_000);

        assertThat(decision.approved()).isFalse();
        assertThat(decision.regime()).isEqualTo(MarketRegime.PANIC);
    }

    @Test
    void sideways_distributionRegimeIsRejectedWithoutInvokingRiskEngine() {
        AnalysisContext ctx = anyContext();
        when(marketRegimeDetector.detect(ctx)).thenReturn(MarketRegime.DISTRIBUTION);
        when(strategySelector.select(MarketRegime.DISTRIBUTION)).thenReturn(Optional.empty());

        TradeDecision decision = tradeEngine.decide(ctx, 10_000);

        assertThat(decision.approved()).isFalse();
        assertThat(decision.regime()).isEqualTo(MarketRegime.DISTRIBUTION);
    }

    @Test
    void edgeCase_strategyProducesInvalidSignalStillFlowsThroughRiskEngineAndIsRejected() {
        AnalysisContext ctx = anyContext();
        SignalResult invalidSignal = SignalResult.invalid("BreakoutStrategy");
        TradeDecision rejected = TradeDecision.rejected(MarketRegime.BREAKOUT, invalidSignal, "strategy produced no valid signal");

        when(marketRegimeDetector.detect(ctx)).thenReturn(MarketRegime.BREAKOUT);
        when(strategySelector.select(MarketRegime.BREAKOUT)).thenReturn(Optional.of(strategy));
        when(strategy.evaluate(ctx)).thenReturn(invalidSignal);
        when(riskEngine.evaluate(MarketRegime.BREAKOUT, invalidSignal, 10_000)).thenReturn(rejected);

        TradeDecision decision = tradeEngine.decide(ctx, 10_000);

        assertThat(decision.approved()).isFalse();
    }
}
