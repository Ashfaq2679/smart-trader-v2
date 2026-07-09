package com.smarttrader.v2.orchestration;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.context.AnalysisContextBuilder;
import com.smarttrader.v2.engine.TradeEngine;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingSchedulerTest {

    @Mock
    private ProductService productService;
    @Mock
    private AnalysisContextBuilder analysisContextBuilder;
    @Mock
    private TradeEngine tradeEngine;
    @Mock
    private DecisionExecutionService decisionExecutionService;

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private TradingScheduler scheduler(SchedulerProperties properties) {
        return new TradingScheduler(productService, analysisContextBuilder, tradeEngine,
                decisionExecutionService, properties, FIXED_CLOCK);
    }

    private SchedulerProperties props() {
        return new SchedulerProperties(true, List.of(), "ONE_HOUR", 60000, "trader-1", 10_000, 0.01);
    }

    private List<Candle> candles(int count) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candles.add(Candle.builder().timestamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(3600L * i))
                    .open(100).high(101).low(99).close(100).volume(10).build());
        }
        return candles;
    }

    private TradeDecision approvedDecision() {
        SignalResult signal = SignalResult.builder().valid(true).strategyName("PullbackStrategy")
                .direction(TradeDirection.LONG).entry(100.0).entryType(EntryType.LIMIT)
                .validityWindow(Duration.ofMinutes(15)).stop(95.0).target(110.0).riskReward(2.0).build();
        return TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK).regimeConfidence(0.8)
                .signal(signal).effectiveRiskReward(2.0).positionSize(10).reason("approved").build();
    }

    @Test
    void bullish_approvedDecisionIsHandedToDecisionExecutionService() {
        TradingScheduler scheduler = scheduler(props());
        when(productService.findProductIdToProcess()).thenReturn(List.of("BTC-USD"));
        List<Candle> candleList = candles(AnalysisContextBuilder.MIN_CANDLES);
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(candleList);
        AnalysisContext ctx = AnalysisContext.builder().price(100.0).build();
        when(analysisContextBuilder.build(eq(candleList), any())).thenReturn(ctx);
        TradeDecision decision = approvedDecision();
        when(tradeEngine.decide(eq(ctx), eq("BTC-USD"), eq(10_000.0), eq(0.01), any(Double.class), any(Double.class), anyString()))
                .thenReturn(decision);

        scheduler.run();

        verify(decisionExecutionService).execute(eq(decision), eq("BTC-USD"), eq("trader-1"), eq(100.0), anyString());
    }

    @Test
    void bearish_holdDecisionNeverReachesDecisionExecutionService() {
        TradingScheduler scheduler = scheduler(props());
        when(productService.findProductIdToProcess()).thenReturn(List.of("BTC-USD"));
        List<Candle> candleList = candles(AnalysisContextBuilder.MIN_CANDLES);
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(candleList);
        AnalysisContext ctx = AnalysisContext.builder().price(100.0).build();
        when(analysisContextBuilder.build(eq(candleList), any())).thenReturn(ctx);
        when(tradeEngine.decide(eq(ctx), eq("BTC-USD"), eq(10_000.0), eq(0.01), any(Double.class), any(Double.class), anyString()))
                .thenReturn(TradeDecision.rejected(MarketRegime.DISTRIBUTION, SignalResult.invalid("none"), "no strategy defined"));

        scheduler.run();

        verify(decisionExecutionService, never()).execute(any(), anyString(), anyString(), any(Double.class), anyString());
    }

    @Test
    void sideways_insufficientCandleHistorySkipsProductWithoutError() {
        TradingScheduler scheduler = scheduler(props());
        when(productService.findProductIdToProcess()).thenReturn(List.of("BTC-USD"));
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(candles(5));

        assertThatCode(scheduler::run).doesNotThrowAnyException();

        verify(decisionExecutionService, never()).execute(any(), anyString(), anyString(), any(Double.class), anyString());
    }

    @Test
    void edgeCase_unknownGranularitySkipsTheWholeRunWithoutThrowing() {
        SchedulerProperties properties = new SchedulerProperties(true, List.of(), "NOT_A_GRANULARITY", 60000, "trader-1", 10_000, 0.01);
        TradingScheduler scheduler = scheduler(properties);

        assertThatCode(scheduler::run).doesNotThrowAnyException();

        verify(productService, never()).findProductIdToProcess();
        verify(productService, never()).getLiveCandles(anyString(), any());
    }

    @Test
    void edgeCase_oneProductFailingDoesNotStopProcessingOthers() {
        TradingScheduler scheduler = scheduler(props());
        when(productService.findProductIdToProcess()).thenReturn(List.of("BTC-USD", "ETH-USD"));
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenThrow(new RuntimeException("exchange error"));
        List<Candle> ethCandles = candles(AnalysisContextBuilder.MIN_CANDLES);
        when(productService.getLiveCandles("ETH-USD", Granularity.ONE_HOUR)).thenReturn(ethCandles);
        AnalysisContext ctx = AnalysisContext.builder().price(50.0).build();
        when(analysisContextBuilder.build(eq(ethCandles), any())).thenReturn(ctx);
        when(tradeEngine.decide(eq(ctx), eq("ETH-USD"), eq(10_000.0), eq(0.01), any(Double.class), any(Double.class), anyString()))
                .thenReturn(TradeDecision.rejected(MarketRegime.DISTRIBUTION, SignalResult.invalid("none"), "no strategy defined"));

        assertThatCode(scheduler::run).doesNotThrowAnyException();

        verify(productService).getLiveCandles("ETH-USD", Granularity.ONE_HOUR);
    }

    @Test
    void edgeCase_noProductsToProcessIsANoOp() {
        TradingScheduler scheduler = scheduler(props());
        when(productService.findProductIdToProcess()).thenReturn(List.of());

        assertThatCode(scheduler::run).doesNotThrowAnyException();
        verify(productService, never()).getLiveCandles(anyString(), any());
    }
}
