package com.smarttrader.v2.context;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.TrendDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisContextBuilderTest {

    private final AnalysisContextBuilder builder = new AnalysisContextBuilder();
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    private Candle candle(int index, double close, double volume) {
        double open = close - 0.1;
        double high = close + 0.2;
        double low = close - 0.3;
        return Candle.builder()
                .timestamp(BASE.plusSeconds(3600L * index))
                .open(open).high(high).low(low).close(close).volume(volume)
                .build();
    }

    private List<Candle> steadyUptrend(int count) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candles.add(candle(i, 100.0 + i, 10.0));
        }
        return candles;
    }

    @Test
    void bullish_steadyUptrendProducesUpTrendAndAboveEma() {
        List<Candle> candles = steadyUptrend(60);

        AnalysisContext ctx = builder.build(candles, BASE.plusSeconds(3600L * 60));

        assertThat(ctx.trendDirection()).isEqualTo(TrendDirection.UP);
        assertThat(ctx.isAboveEMA()).isTrue();
        assertThat(ctx.price()).isEqualTo(candles.get(candles.size() - 1).close());
    }

    @Test
    void bearish_steadyDowntrendProducesDownTrend() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            candles.add(candle(i, 200.0 - i, 10.0));
        }

        AnalysisContext ctx = builder.build(candles, BASE.plusSeconds(3600L * 60));

        assertThat(ctx.trendDirection()).isEqualTo(TrendDirection.DOWN);
        assertThat(ctx.isAboveEMA()).isFalse();
    }

    @Test
    void sideways_flatPricesProduceSidewaysTrendAndNoVolumeSpike() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            candles.add(candle(i, 100.0, 10.0));
        }

        AnalysisContext ctx = builder.build(candles, BASE.plusSeconds(3600L * 60));

        assertThat(ctx.trendDirection()).isEqualTo(TrendDirection.SIDEWAYS);
        assertThat(ctx.volumeSpike()).isFalse();
    }

    @Test
    void edgeCase_volumeSpikeDetectedWhenLastCandleVolumeExceedsThreshold() {
        List<Candle> candles = steadyUptrend(60);
        Candle last = candles.get(candles.size() - 1);
        candles.set(candles.size() - 1, Candle.builder()
                .timestamp(last.timestamp()).open(last.open()).high(last.high()).low(last.low())
                .close(last.close()).volume(last.volume() * 5) // way above 1.8x average
                .build());

        AnalysisContext ctx = builder.build(candles, BASE.plusSeconds(3600L * 60));

        assertThat(ctx.volumeSpike()).isTrue();
    }

    @Test
    void edgeCase_tooFewCandlesThrows() {
        List<Candle> candles = steadyUptrend(10);

        assertThatThrownBy(() -> builder.build(candles, BASE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void edgeCase_dataLatencyMsReflectsGapBetweenLastCandleAndNow() {
        List<Candle> candles = steadyUptrend(60);
        Instant lastCandleTime = candles.get(candles.size() - 1).timestamp();
        Instant now = lastCandleTime.plusSeconds(120);

        AnalysisContext ctx = builder.build(candles, now);

        assertThat(ctx.dataLatencyMs()).isEqualTo(120_000);
        assertThat(ctx.lastCandleCloseTime()).isEqualTo(lastCandleTime);
    }
}
