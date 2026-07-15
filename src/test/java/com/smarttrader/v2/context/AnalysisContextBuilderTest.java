package com.smarttrader.v2.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.liquidity.LiquidityMapperService;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.LiquidityMap;
import com.smarttrader.v2.model.TrendDirection;
import com.smarttrader.v2.service.ProductService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisContextBuilderTest {

    @Mock
    private ProductService productService;
    @Mock
    private LiquidityMapperService liquidityMapper;

    private AnalysisContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AnalysisContextBuilder(productService, liquidityMapper);
    }

    private List<Candle> steadyUptrend(int count, double startClose, double step) {
        List<Candle> candles = new ArrayList<>();
        double close = startClose;
        for (int i = 0; i < count; i++) {
            double open = close;
            close = close + step;
            candles.add(Candle.builder().timestamp(Instant.ofEpochSecond(3600L * i))
                    .open(open).high(Math.max(open, close) + 0.1).low(Math.min(open, close) - 0.1)
                    .close(close).volume(10).build());
        }
        return candles;
    }

    @Test
    void bullish_steadyUptrendClassifiesTrendDirectionAsUp() {
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 100.0, 0.5);

        AnalysisContext ctx = builder.buildV22Context(candles);

        assertThat(ctx.trendDirection()).isEqualTo(TrendDirection.UP);
        assertThat(ctx.ema9()).isGreaterThan(ctx.ema50());
        assertThat(ctx.isAboveEMA()).isTrue();
    }

    @Test
    void bearish_steadyDowntrendClassifiesTrendDirectionAsDown() {
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 200.0, -0.5);

        AnalysisContext ctx = builder.buildV22Context(candles);

        assertThat(ctx.trendDirection()).isEqualTo(TrendDirection.DOWN);
    }

    @Test
    void sideways_flatCandlesClassifyAsSidewaysWithZeroConsolidationRange() {
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 100.0, 0.0);

        AnalysisContext ctx = builder.buildV22Context(candles);

        assertThat(ctx.trendDirection()).isEqualTo(TrendDirection.SIDEWAYS);
    }

    @Test
    void edgeCase_fewerThanMinCandlesThrows() {
        List<Candle> candles = steadyUptrend(10, 100.0, 0.5);

        assertThatThrownBy(() -> builder.buildV22Context(candles))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void edgeCase_buildAttachesTheLiquidityMapFromLiquidityMapperService() {
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 100.0, 0.5);
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(candles);
        LiquidityMap expectedMap = new LiquidityMap(List.of(), 123L);
        when(liquidityMapper.mapLiquidity(org.mockito.ArgumentMatchers.eq("BTC-USD"), any())).thenReturn(expectedMap);

        AnalysisContext ctx = builder.build("BTC-USD", Granularity.ONE_HOUR);

        assertThat(ctx.liquidityMap()).isEqualTo(expectedMap);
        assertThat(ctx.price()).isGreaterThan(0);
    }
}
