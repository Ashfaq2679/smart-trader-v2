package com.smarttrader.v2.positioning;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.smarttrader.v2.model.Match;

import static org.assertj.core.api.Assertions.assertThat;

class CVDCalculatorServiceTest {

    private final CVDCalculatorService service = new CVDCalculatorService();

    private Match match(String side, double volume) {
        return Match.builder().symbol("BTC-USD").takerSide(side).volume(volume).timestamp(Instant.now()).build();
    }

    @Test
    void bullish_buyHeavyMatchesIncreaseCvd() {
        service.onMatchesStream("BTC-USD", List.of(match("BUY", 10), match("BUY", 5), match("SELL", 3)));

        assertThat(service.getCVD1m("BTC-USD")).isEqualTo(12.0); // 10 + 5 - 3
    }

    @Test
    void bearish_sellHeavyMatchesDecreaseCvd() {
        service.onMatchesStream("BTC-USD", List.of(match("SELL", 10), match("SELL", 5), match("BUY", 2)));

        assertThat(service.getCVD1m("BTC-USD")).isEqualTo(-13.0);
    }

    @Test
    void sideways_unknownSymbolDefaultsToZero() {
        assertThat(service.getCVD1m("UNKNOWN")).isZero();
    }

    @Test
    void edgeCase_slopeIsZeroWithFewerThanTwentySamples() {
        for (int i = 0; i < 19; i++) {
            service.onMatchesStream("BTC-USD", List.of(match("BUY", 1)));
        }

        assertThat(service.getCVDSlope5m("BTC-USD")).isZero();
    }

    @Test
    void bullish_monotonicallyIncreasingCvdProducesAPositiveSlope() {
        for (int i = 0; i < 25; i++) {
            service.onMatchesStream("BTC-USD", List.of(match("BUY", 2)));
        }

        assertThat(service.getCVDSlope5m("BTC-USD")).isPositive();
    }

    @Test
    void bullish_isNewHighTrueWhenLatestSampleIsTheMaximum() {
        service.onMatchesStream("BTC-USD", List.of(match("BUY", 5)));
        service.onMatchesStream("BTC-USD", List.of(match("BUY", 1)));
        service.onMatchesStream("BTC-USD", List.of(match("BUY", 10)));

        assertThat(service.isNewHigh("BTC-USD", 20)).isTrue();
    }

    @Test
    void bearish_isNewHighFalseWhenLatestSampleIsBelowAnEarlierPeak() {
        service.onMatchesStream("BTC-USD", List.of(match("BUY", 20))); // cvd=20, peak
        service.onMatchesStream("BTC-USD", List.of(match("SELL", 15))); // cvd=5

        assertThat(service.isNewHigh("BTC-USD", 20)).isFalse();
    }

    @Test
    void edgeCase_isNewHighFalseWithNoHistory() {
        assertThat(service.isNewHigh("UNKNOWN", 20)).isFalse();
    }
}
