package com.smarttrader.v2.engine;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.event.DomainEventPublisher;
import com.smarttrader.v2.integrity.DataIntegrityException;
import com.smarttrader.v2.integrity.DataIntegrityValidator;
import com.smarttrader.v2.integrity.DataIntegrityViolationType;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.MarketRegimeResult;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.regime.MarketRegimeDetector;
import com.smarttrader.v2.risk.GlobalRiskCheck;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private GlobalRiskCheck globalRiskCheck;
    @Mock
    private DataIntegrityValidator dataIntegrityValidator;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private TradingStrategy strategy;

    private TradeEngine tradeEngine;

    private static final String PRODUCT_ID = "BTC-USD";
    private static final double CAPITAL = 10_000;

    @BeforeEach
    void setUp() {
        tradeEngine = new TradeEngine(marketRegimeDetector, strategySelector, riskEngine, globalRiskCheck,
                dataIntegrityValidator, eventPublisher);
    }

    private AnalysisContext anyContext() {
        return AnalysisContext.builder().build();
    }

    private MarketRegimeResult regimeResult(MarketRegime regime, double confidence) {
        return MarketRegimeResult.builder().regime(regime).confidence(confidence).build();
    }

    private void stubGlobalRiskCheckPassThrough() {
        when(globalRiskCheck.apply(any(), eq(PRODUCT_ID), eq(CAPITAL), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void bullish_pullbackRegimeRoutesThroughStrategyAndRiskEngine() {
        AnalysisContext ctx = anyContext();
        SignalResult signal = SignalResult.builder().valid(true).strategyName("PullbackStrategy")
                .direction(TradeDirection.LONG).entry(100).stop(95).target(110).riskReward(2.0).build();
        TradeDecision expected = TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK)
                .regimeConfidence(0.8).signal(signal).positionSize(10).reason("approved").build();

        when(marketRegimeDetector.detect(ctx)).thenReturn(regimeResult(MarketRegime.PULLBACK, 0.8));
        when(strategySelector.select(MarketRegime.PULLBACK)).thenReturn(Optional.of(strategy));
        when(strategy.evaluate(ctx)).thenReturn(signal);
        when(riskEngine.evaluate(MarketRegime.PULLBACK, signal, CAPITAL, RiskEngine.DEFAULT_RISK_PERCENT,
                TradingConstants.DEFAULT_FEES, TradingConstants.DEFAULT_SLIPPAGE, 0.8))
                .thenReturn(expected);
        stubGlobalRiskCheckPassThrough();

        TradeDecision decision = tradeEngine.decide(ctx, PRODUCT_ID, CAPITAL);

        assertThat(decision).isEqualTo(expected);
    }

    @Test
    void bearish_panicRegimeIsRejectedWithoutInvokingRiskEngine() {
        AnalysisContext ctx = anyContext();
        when(marketRegimeDetector.detect(ctx)).thenReturn(regimeResult(MarketRegime.PANIC, 0.9));
        when(strategySelector.select(MarketRegime.PANIC)).thenReturn(Optional.empty());
        stubGlobalRiskCheckPassThrough();

        TradeDecision decision = tradeEngine.decide(ctx, PRODUCT_ID, CAPITAL);

        assertThat(decision.approved()).isFalse();
        assertThat(decision.regime()).isEqualTo(MarketRegime.PANIC);
        assertThat(decision.regimeConfidence()).isEqualTo(0.9);
    }

    @Test
    void sideways_distributionRegimeIsRejectedWithoutInvokingRiskEngine() {
        AnalysisContext ctx = anyContext();
        when(marketRegimeDetector.detect(ctx)).thenReturn(regimeResult(MarketRegime.DISTRIBUTION, 0.3));
        when(strategySelector.select(MarketRegime.DISTRIBUTION)).thenReturn(Optional.empty());
        stubGlobalRiskCheckPassThrough();

        TradeDecision decision = tradeEngine.decide(ctx, PRODUCT_ID, CAPITAL);

        assertThat(decision.approved()).isFalse();
        assertThat(decision.regime()).isEqualTo(MarketRegime.DISTRIBUTION);
    }

    @Test
    void edgeCase_strategyProducesInvalidSignalStillFlowsThroughRiskEngineAndIsRejected() {
        AnalysisContext ctx = anyContext();
        SignalResult invalidSignal = SignalResult.invalid("BreakoutStrategy");
        TradeDecision rejected = TradeDecision.rejected(MarketRegime.BREAKOUT, invalidSignal, "strategy produced no valid signal");

        when(marketRegimeDetector.detect(ctx)).thenReturn(regimeResult(MarketRegime.BREAKOUT, 0.7));
        when(strategySelector.select(MarketRegime.BREAKOUT)).thenReturn(Optional.of(strategy));
        when(strategy.evaluate(ctx)).thenReturn(invalidSignal);
        when(riskEngine.evaluate(MarketRegime.BREAKOUT, invalidSignal, CAPITAL, RiskEngine.DEFAULT_RISK_PERCENT,
                TradingConstants.DEFAULT_FEES, TradingConstants.DEFAULT_SLIPPAGE, 0.7))
                .thenReturn(rejected);
        stubGlobalRiskCheckPassThrough();

        TradeDecision decision = tradeEngine.decide(ctx, PRODUCT_ID, CAPITAL);

        assertThat(decision.approved()).isFalse();
    }

    @Test
    void edgeCase_staleDataIsRejectedBeforeAnyRegimeDetectionRuns() {
        AnalysisContext ctx = anyContext();
        org.mockito.Mockito.doThrow(new DataIntegrityException(DataIntegrityViolationType.STALE_DATA, "too stale"))
                .when(dataIntegrityValidator).rejectStaleData(ctx);

        assertThatThrownBy(() -> tradeEngine.decide(ctx, PRODUCT_ID, CAPITAL))
                .isInstanceOf(DataIntegrityException.class);

        verifyNoInteractions(marketRegimeDetector, strategySelector, riskEngine, globalRiskCheck);
    }
}
