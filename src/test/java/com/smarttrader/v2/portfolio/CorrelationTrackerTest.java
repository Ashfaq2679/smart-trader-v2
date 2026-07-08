package com.smarttrader.v2.portfolio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class CorrelationTrackerTest {

    private final CorrelationTracker tracker = new CorrelationTracker();

    private void feed(String productId, double... prices) {
        for (double price : prices) {
            tracker.recordPrice(productId, price);
        }
    }

    @Test
    void bullish_perfectlyCorrelatedProductsReturnCorrelationCloseToOne() {
        feed("BTC-USD", 100, 101, 103, 102, 105, 108);
        feed("ETH-USD", 10, 10.1, 10.3, 10.2, 10.5, 10.8);

        assertThat(tracker.correlation("BTC-USD", "ETH-USD")).hasValueSatisfying(
                correlation -> assertThat(correlation).isCloseTo(1.0, offset(0.01)));
    }

    @Test
    void bearish_inverselyCorrelatedProductsReturnCorrelationCloseToNegativeOne() {
        feed("BTC-USD", 100, 101, 103, 102, 105, 108);
        feed("GOLD", 100, 99, 97, 98, 95, 92);

        assertThat(tracker.correlation("BTC-USD", "GOLD")).hasValueSatisfying(
                correlation -> assertThat(correlation).isCloseTo(-1.0, offset(0.01)));
    }

    @Test
    void sideways_sameProductIsAlwaysFullyCorrelated() {
        assertThat(tracker.correlation("BTC-USD", "BTC-USD")).contains(1.0);
    }

    @Test
    void edgeCase_insufficientHistoryReturnsEmpty() {
        feed("BTC-USD", 100, 101);
        feed("ETH-USD", 10, 10.1);

        assertThat(tracker.correlation("BTC-USD", "ETH-USD")).isEmpty();
    }

    @Test
    void edgeCase_unknownProductReturnsEmpty() {
        feed("BTC-USD", 100, 101, 103, 102, 105, 108);

        assertThat(tracker.correlation("BTC-USD", "UNKNOWN-USD")).isEmpty();
    }

    @Test
    void edgeCase_rollingWindowDropsOldestReturnsBeyondWindowSize() {
        for (int i = 0; i < 100; i++) {
            tracker.recordPrice("BTC-USD", 100 + i);
            tracker.recordPrice("ETH-USD", 10 + i * 0.1);
        }

        assertThat(tracker.correlation("BTC-USD", "ETH-USD")).hasValueSatisfying(
                correlation -> assertThat(correlation).isCloseTo(1.0, offset(0.01)));
    }
}
