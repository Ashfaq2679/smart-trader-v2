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
import com.smarttrader.v2.positioning.CVDCalculatorService;
import com.smarttrader.v2.positioning.FundingMonitorService;
import com.smarttrader.v2.positioning.OIMonitorService;
import com.smarttrader.v2.service.ProductService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisContextBuilderTest {

    @Mock
    private ProductService productService;
    @Mock
    private LiquidityMapperService liquidityMapper;
    @Mock
    private CVDCalculatorService cvdCalculator;
    @Mock
    private FundingMonitorService fundingMonitor;
    @Mock
    private OIMonitorService oiMonitor;

    private AnalysisContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AnalysisContextBuilder(productService, liquidityMapper, cvdCalculator, fundingMonitor, oiMonitor);
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

    private void stubCommonCollaborators(List<Candle> candles) {
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(candles);
        when(liquidityMapper.mapLiquidity(eq("BTC-USD"), any())).thenReturn(new LiquidityMap(List.of(), 123L));
    }

    @Test
    void edgeCase_buildAttachesTheLiquidityMapFromLiquidityMapperService() {
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 100.0, 0.5);
        stubCommonCollaborators(candles);

        AnalysisContext ctx = builder.build("BTC-USD", Granularity.ONE_HOUR);

        assertThat(ctx.liquidityMap()).isEqualTo(new LiquidityMap(List.of(), 123L));
        assertThat(ctx.price()).isGreaterThan(0);
    }

    @Test
    void bullish_buildAttachesCvdAndFundingAndOiFromTheirRespectiveServices() {
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 100.0, 0.5);
        stubCommonCollaborators(candles);
        when(cvdCalculator.getCVD1m("BTC-USD")).thenReturn(42.0);
        when(cvdCalculator.getCVDSlope5m("BTC-USD")).thenReturn(1.5);
        when(fundingMonitor.getCurrentFundingRateBps("BTC-USD")).thenReturn(12.0);
        when(fundingMonitor.getFundingPercentile30d("BTC-USD")).thenReturn(92);
        when(oiMonitor.getOIChange1h("BTC-USD")).thenReturn(0.05);
        when(oiMonitor.getOIChange24h("BTC-USD")).thenReturn(0.20);

        AnalysisContext ctx = builder.build("BTC-USD", Granularity.ONE_HOUR);

        assertThat(ctx.cvd1m()).isEqualTo(42.0);
        assertThat(ctx.cvdSlope5m()).isEqualTo(1.5);
        assertThat(ctx.fundingRateBps()).isEqualTo(12.0);
        assertThat(ctx.fundingPercentile30d()).isEqualTo(92);
        assertThat(ctx.oiChange1h()).isEqualTo(0.05);
        assertThat(ctx.oiChange24h()).isEqualTo(0.20);
    }

    @Test
    void bearish_priceNewHighWithoutCvdConfirmationIsFlaggedAsDivergence() {
        // Steady uptrend -> the latest close is always a new high over the prior window.
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 100.0, 0.5);
        stubCommonCollaborators(candles);
        when(cvdCalculator.isNewHigh(eq("BTC-USD"), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);

        AnalysisContext ctx = builder.build("BTC-USD", Granularity.ONE_HOUR);

        assertThat(ctx.cvdDivergence()).isTrue();
    }

    @Test
    void sideways_priceNewHighConfirmedByCvdIsNotDivergence() {
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 100.0, 0.5);
        stubCommonCollaborators(candles);
        when(cvdCalculator.isNewHigh(eq("BTC-USD"), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);

        AnalysisContext ctx = builder.build("BTC-USD", Granularity.ONE_HOUR);

        assertThat(ctx.cvdDivergence()).isFalse();
    }

    @Test
    void edgeCase_oiConfirmsUpOnlyWhenPriceRisingAndOiRising() {
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 100.0, 0.5); // rising
        stubCommonCollaborators(candles);
        when(oiMonitor.getOIChange1h("BTC-USD")).thenReturn(0.03);

        AnalysisContext ctx = builder.build("BTC-USD", Granularity.ONE_HOUR);

        assertThat(ctx.oiConfirmsUp()).isTrue();
        assertThat(ctx.oiConfirmsDown()).isFalse();
    }

    @Test
    void edgeCase_oiConfirmsDownOnlyWhenPriceFallingAndOiRising() {
        List<Candle> candles = steadyUptrend(AnalysisContextBuilder.MIN_CANDLES, 200.0, -0.5); // falling
        stubCommonCollaborators(candles);
        when(oiMonitor.getOIChange1h("BTC-USD")).thenReturn(0.03);

        AnalysisContext ctx = builder.build("BTC-USD", Granularity.ONE_HOUR);

        assertThat(ctx.oiConfirmsDown()).isTrue();
        assertThat(ctx.oiConfirmsUp()).isFalse();
    }
}
