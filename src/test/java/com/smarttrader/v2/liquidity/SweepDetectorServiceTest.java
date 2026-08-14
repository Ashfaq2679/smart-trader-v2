package com.smarttrader.v2.liquidity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.event.LiquiditySweepDetectedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.LiquidityMap;
import com.smarttrader.v2.model.LiquidityPool;
import com.smarttrader.v2.model.PoolType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SweepDetectorServiceTest {

    @Mock
    private LiquidityMapperService liquidityMapper;
    @Mock
    private TradingEventPublisher eventPublisher;

    private SweepDetectorService sweepDetectorService;
    private final AnalysisContext ctx = AnalysisContext.builder().atr(10.0).build();

    @BeforeEach
    void setUp() {
        sweepDetectorService = new SweepDetectorService(liquidityMapper, eventPublisher);
    }

    private Candle candle(double open, double high, double low, double close) {
        return Candle.builder().timestamp(Instant.now()).open(open).high(high).low(low).close(close).volume(10).build();
    }

    private LiquidityPool pool(double level, PoolType type, float density) {
        return LiquidityPool.builder().symbol("BTC-USD").level(BigDecimal.valueOf(level)).type(type).density(density).build();
    }

    @Test
    void bullish_wickBelowEqlThenCloseAboveIsDetectedAsAnUpwardSweep() {
        LiquidityPool eql = pool(100.0, PoolType.EQL, 70f);
        when(liquidityMapper.mapLiquidity("BTC-USD", ctx)).thenReturn(new LiquidityMap(List.of(eql), 0));
        Candle sweepCandle = candle(101, 102, 98, 101); // wicks below 100, closes above it

        sweepDetectorService.onCandleClose("BTC-USD", sweepCandle, ctx);

        ArgumentCaptor<LiquiditySweepDetectedEvent> captor = ArgumentCaptor.forClass(LiquiditySweepDetectedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().side).isEqualTo("UP");
        assertThat(captor.getValue().symbol).isEqualTo("BTC-USD");
        assertThat(captor.getValue().density).isEqualTo(70f);
        assertThat(captor.getValue().reclaimed).isTrue();
    }

    @Test
    void bearish_wickAboveEqhThenCloseBelowIsDetectedAsADownwardSweep() {
        LiquidityPool eqh = pool(100.0, PoolType.EQH, 80f);
        when(liquidityMapper.mapLiquidity("BTC-USD", ctx)).thenReturn(new LiquidityMap(List.of(eqh), 0));
        Candle sweepCandle = candle(99, 102, 97, 99); // wicks above 100, closes below it

        sweepDetectorService.onCandleClose("BTC-USD", sweepCandle, ctx);

        ArgumentCaptor<LiquiditySweepDetectedEvent> captor = ArgumentCaptor.forClass(LiquiditySweepDetectedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().side).isEqualTo("DOWN");
    }

    @Test
    void sideways_priceStayingWellInsideThePoolNeverSweeps() {
        LiquidityPool eqh = pool(200.0, PoolType.EQH, 80f);
        when(liquidityMapper.mapLiquidity("BTC-USD", ctx)).thenReturn(new LiquidityMap(List.of(eqh), 0));
        Candle quietCandle = candle(101, 102, 99, 100);

        sweepDetectorService.onCandleClose("BTC-USD", quietCandle, ctx);

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void edgeCase_wickThroughLevelWithoutReclaimingIsNotASweep() {
        // Wicks below the pool but closes below it too (no reclaim back inside)
        LiquidityPool eql = pool(100.0, PoolType.EQL, 70f);
        when(liquidityMapper.mapLiquidity("BTC-USD", ctx)).thenReturn(new LiquidityMap(List.of(eql), 0));
        Candle noReclaim = candle(99, 100, 95, 96);

        sweepDetectorService.onCandleClose("BTC-USD", noReclaim, ctx);

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void edgeCase_multiplePoolsCanEachEmitTheirOwnSweep() {
        LiquidityPool eql = pool(100.0, PoolType.EQL, 70f);
        LiquidityPool eqh = pool(103.0, PoolType.EQH, 65f);
        when(liquidityMapper.mapLiquidity("BTC-USD", ctx)).thenReturn(new LiquidityMap(List.of(eql, eqh), 0));
        // Wide bar: wicks below EQL and above EQH, closes between them (inside both -> both reclaimed)
        Candle wideCandle = candle(101, 104, 98, 101);

        sweepDetectorService.onCandleClose("BTC-USD", wideCandle, ctx);

        verify(eventPublisher, org.mockito.Mockito.times(2)).publish(any());
    }
}
