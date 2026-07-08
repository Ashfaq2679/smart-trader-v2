package com.smarttrader.v2.integrity;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataIntegrityValidatorTest {

    private final DataIntegrityValidator validator = new DataIntegrityValidator();

    private Candle candleAt(long epochSecond, double open, double close) {
        return Candle.builder().timestamp(Instant.ofEpochSecond(epochSecond))
                .open(open).high(Math.max(open, close)).low(Math.min(open, close)).close(close).volume(1).build();
    }

    // --- rejectStaleData ---

    @Test
    void bullish_freshDataPassesStaleCheck() {
        AnalysisContext ctx = AnalysisContext.builder().dataLatencyMs(100).build();

        assertThatCode(() -> validator.rejectStaleData(ctx)).doesNotThrowAnyException();
    }

    @Test
    void bearish_staleDataIsRejected() {
        AnalysisContext ctx = AnalysisContext.builder().dataLatencyMs(999_999).build();

        assertThatThrownBy(() -> validator.rejectStaleData(ctx))
                .isInstanceOf(DataIntegrityException.class)
                .satisfies(ex -> assertThat(((DataIntegrityException) ex).violationType())
                        .isEqualTo(DataIntegrityViolationType.STALE_DATA));
    }

    // --- ensureCandleSequenceContinuity ---

    @Test
    void bullish_contiguousCandlesAtExactGranularityIntervalPass() {
        List<Candle> candles = List.of(
                candleAt(0, 1, 1), candleAt(3600, 1, 1), candleAt(7200, 1, 1));

        assertThatCode(() -> validator.ensureCandleSequenceContinuity(candles, Granularity.ONE_HOUR))
                .doesNotThrowAnyException();
    }

    @Test
    void bearish_gapBetweenCandlesIsRejected() {
        List<Candle> candles = List.of(candleAt(0, 1, 1), candleAt(7200, 1, 1)); // 2h gap, not 1h

        assertThatThrownBy(() -> validator.ensureCandleSequenceContinuity(candles, Granularity.ONE_HOUR))
                .isInstanceOf(DataIntegrityException.class)
                .satisfies(ex -> assertThat(((DataIntegrityException) ex).violationType())
                        .isEqualTo(DataIntegrityViolationType.SEQUENCE_GAP));
    }

    @Test
    void edgeCase_singleCandleListIsTriviallyContinuous() {
        assertThatCode(() -> validator.ensureCandleSequenceContinuity(List.of(candleAt(0, 1, 1)), Granularity.ONE_HOUR))
                .doesNotThrowAnyException();
    }

    @Test
    void edgeCase_duplicateTimestampIsRejectedAsNonContiguous() {
        List<Candle> candles = List.of(candleAt(0, 1, 1), candleAt(0, 1, 1));

        assertThatThrownBy(() -> validator.ensureCandleSequenceContinuity(candles, Granularity.ONE_HOUR))
                .isInstanceOf(DataIntegrityException.class);
    }

    // --- validatePriceGaps ---

    @Test
    void bullish_smallOpenCloseMoveIsValid() {
        List<Candle> candles = List.of(candleAt(0, 100, 102), candleAt(3600, 102.5, 103));

        assertThatCode(() -> validator.validatePriceGaps(candles)).doesNotThrowAnyException();
    }

    @Test
    void bearish_largePriceGapIsRejected() {
        List<Candle> candles = List.of(candleAt(0, 100, 100), candleAt(3600, 150, 155)); // 50% gap

        assertThatThrownBy(() -> validator.validatePriceGaps(candles))
                .isInstanceOf(DataIntegrityException.class)
                .satisfies(ex -> assertThat(((DataIntegrityException) ex).violationType())
                        .isEqualTo(DataIntegrityViolationType.PRICE_GAP));
    }

    @Test
    void edgeCase_gapExactlyAtThresholdIsValid() {
        List<Candle> candles = List.of(candleAt(0, 100, 100), candleAt(3600, 110, 110)); // exactly 10%

        assertThatCode(() -> validator.validatePriceGaps(candles)).doesNotThrowAnyException();
    }
}
