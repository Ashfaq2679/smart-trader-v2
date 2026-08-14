package com.smarttrader.v2.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.context.AnalysisContextBuilder;
import com.smarttrader.v2.engine.TradeEngine;
import com.smarttrader.v2.execution.OrderService;
import com.smarttrader.v2.execution.PositionService;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.service.ProductService;

@ExtendWith(MockitoExtension.class)
class TradingSchedulerTest {

    @Mock
    private AnalysisContextBuilder contextBuilder;

    @Mock
    private TradeEngine tradeEngine;

    @Mock
    private OrderService orderService;

    @Mock
    private PositionService positionService;
    
    @Mock
    private ProductService productService;

    private TradingScheduler scheduler() {
        return new TradingScheduler(contextBuilder, tradeEngine, orderService, positionService, productService);
    }

    private void configure(TradingScheduler scheduler, boolean schedulerEnabled, boolean granularityEnabled, List<String> symbols) {
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", schedulerEnabled);
        ReflectionTestUtils.setField(scheduler, "trackedSymbols", symbols);
        ReflectionTestUtils.setField(scheduler, "capital", 10_000.0);
        ReflectionTestUtils.setField(scheduler, "fifteenMinuteEnabled", granularityEnabled);
    }

    private TradeDecision decision(boolean approved) {
        return TradeDecision.builder()
                .approved(approved)
                .regime(MarketRegime.PULLBACK)
                .signal(SignalResult.invalid("PullbackStrategy"))
                .positionSize(0)
                .reason("test")
                .build();
    }

    @Test
    void bullish_globalAndGranularityEnabledPollsEveryTrackedSymbol() {
        TradingScheduler scheduler = scheduler();
        configure(scheduler, true, true, List.of("BTC-USD", "ETH-USD"));

        AnalysisContext ctx = AnalysisContext.builder().build();
        when(contextBuilder.build(any(), eq(Granularity.FIFTEEN_MINUTE))).thenReturn(ctx);
        when(tradeEngine.decide(eq(ctx), anyDouble())).thenReturn(decision(true));

        scheduler.pollFifteenMinute();

        verify(contextBuilder).build("BTC-USD", Granularity.FIFTEEN_MINUTE);
        verify(contextBuilder).build("ETH-USD", Granularity.FIFTEEN_MINUTE);
        verify(tradeEngine, times(2)).decide(eq(ctx), eq(10_000.0));
    }

    @Test
    void bearish_globalDisabledSkipsPollingEntirely() {
        TradingScheduler scheduler = scheduler();
        configure(scheduler, false, true, List.of("BTC-USD"));

        scheduler.pollFifteenMinute();

        verifyNoInteractions(contextBuilder, tradeEngine);
    }

    @Test
    void sideways_granularityDisabledSkipsThatGranularityOnly() {
        TradingScheduler scheduler = scheduler();
        configure(scheduler, true, false, List.of("BTC-USD"));

        scheduler.pollFifteenMinute();

        verifyNoInteractions(contextBuilder, tradeEngine);
    }

    @Test
    void edgeCase_oneSymbolFailingDoesNotStopOthersFromBeingPolled() {
        TradingScheduler scheduler = scheduler();
        configure(scheduler, true, true, List.of("BTC-USD", "ETH-USD"));

        AnalysisContext ctx = AnalysisContext.builder().build();
        when(contextBuilder.build("BTC-USD", Granularity.FIFTEEN_MINUTE)).thenThrow(new RuntimeException("coinbase down"));
        when(contextBuilder.build("ETH-USD", Granularity.FIFTEEN_MINUTE)).thenReturn(ctx);
        when(tradeEngine.decide(eq(ctx), anyDouble())).thenReturn(decision(false));

        scheduler.pollFifteenMinute();

        verify(contextBuilder).build("BTC-USD", Granularity.FIFTEEN_MINUTE);
        verify(contextBuilder).build("ETH-USD", Granularity.FIFTEEN_MINUTE);
        verify(tradeEngine, times(1)).decide(any(), anyDouble());
    }

    @Test
    void edgeCase_emptyTrackedSymbolsPollsNothing() {
        TradingScheduler scheduler = scheduler();
        configure(scheduler, true, true, List.of());

        scheduler.pollFifteenMinute();

        verifyNoInteractions(contextBuilder, tradeEngine);
    }
}
