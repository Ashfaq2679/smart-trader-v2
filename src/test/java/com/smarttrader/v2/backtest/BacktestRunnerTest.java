package com.smarttrader.v2.backtest;

import com.smarttrader.v2.engine.TradeEngine;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.position.Position;
import com.smarttrader.v2.position.PositionService;
import com.smarttrader.v2.position.PositionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestRunnerTest {

    @Mock
    private TradeEngine tradeEngine;
    @Mock
    private PositionService positionService;
    @Mock
    private SimulatedFillEngine simulatedFillEngine;

    private BacktestRunner backtestRunner;

    @BeforeEach
    void setUp() {
        backtestRunner = new BacktestRunner(tradeEngine, positionService, simulatedFillEngine);
    }

    private BacktestTick tick() {
        return BacktestTick.builder().productId("BTC-USD").context(AnalysisContext.builder().build())
                .price(100.0).timestamp(Instant.parse("2026-01-01T00:00:00Z")).build();
    }

    private SignalResult signal() {
        return SignalResult.builder().valid(true).strategyName("PullbackStrategy").direction(TradeDirection.LONG)
                .entry(100.0).entryType(EntryType.MARKET).validityWindow(Duration.ofMinutes(5))
                .stop(95.0).target(110.0).riskReward(2.0).build();
    }

    private TradeDecision approvedDecision() {
        return TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK).regimeConfidence(0.8)
                .signal(signal()).effectiveRiskReward(2.0).positionSize(10).reason("approved").build();
    }

    @Test
    void bullish_approvedDecisionOpensAndFillsAPosition() {
        BacktestTick tick = tick();
        TradeDecision decision = approvedDecision();
        when(tradeEngine.decide(any(), anyString(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), anyString())).thenReturn(decision);

        Position opened = Position.builder().positionId("p1").productId("BTC-USD").status(PositionStatus.PENDING).build();
        Position filled = opened.toBuilder().status(PositionStatus.OPEN).filledSize(10).build();
        when(positionService.open(any(), anyString(), anyString(), any(), anyString())).thenReturn(opened);
        when(simulatedFillEngine.simulateFill(decision.signal(), 100.0)).thenReturn(Optional.of(100.0));
        when(positionService.recordFill(anyString(), org.mockito.ArgumentMatchers.anyDouble(), any(), anyString()))
                .thenReturn(filled);

        List<BacktestStepResult> results = backtestRunner.run(List.of(tick), 10_000);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).decision().approved()).isTrue();
        assertThat(results.get(0).position()).contains(filled);
    }

    @Test
    void bearish_rejectedDecisionOpensNoPosition() {
        BacktestTick tick = tick();
        TradeDecision rejected = TradeDecision.rejected(MarketRegime.PANIC, SignalResult.invalid("none"), "no strategy defined for regime PANIC");
        when(tradeEngine.decide(any(), anyString(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), anyString())).thenReturn(rejected);

        List<BacktestStepResult> results = backtestRunner.run(List.of(tick), 10_000);

        assertThat(results.get(0).position()).isEmpty();
        verify(positionService, never()).open(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void edgeCase_approvedDecisionThatDoesNotFillYetStaysPending() {
        BacktestTick tick = tick();
        TradeDecision decision = approvedDecision();
        when(tradeEngine.decide(any(), anyString(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), anyString())).thenReturn(decision);

        Position opened = Position.builder().positionId("p1").productId("BTC-USD").status(PositionStatus.PENDING).build();
        when(positionService.open(any(), anyString(), anyString(), any(), anyString())).thenReturn(opened);
        when(simulatedFillEngine.simulateFill(decision.signal(), 100.0)).thenReturn(Optional.empty());

        List<BacktestStepResult> results = backtestRunner.run(List.of(tick), 10_000);

        assertThat(results.get(0).position()).contains(opened);
        verify(positionService, never()).recordFill(anyString(), org.mockito.ArgumentMatchers.anyDouble(), any(), anyString());
    }

    @Test
    void multiTickTimelineProducesOneResultPerTickWithDistinctCorrelationIds() {
        when(tradeEngine.decide(any(), anyString(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), anyString()))
                .thenReturn(TradeDecision.rejected(MarketRegime.DISTRIBUTION, SignalResult.invalid("none"), "no strategy defined for regime DISTRIBUTION"));

        List<BacktestStepResult> results = backtestRunner.run(List.of(tick(), tick(), tick()), 10_000);

        assertThat(results).hasSize(3);
    }
}
