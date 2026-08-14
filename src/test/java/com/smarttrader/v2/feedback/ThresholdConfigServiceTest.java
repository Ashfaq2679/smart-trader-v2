package com.smarttrader.v2.feedback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThresholdConfigServiceTest {

    private final ThresholdConfigService service = new ThresholdConfigService();

    @Test
    void bullish_unseenSymbolReturnsEmpty() {
        assertThat(service.getAtrPercentile90("BTC-USD")).isEmpty();
    }

    @Test
    void bearish_updateThenGetReturnsTheStoredValue() {
        service.updateAtrPercentile90("BTC-USD", 123.4);

        assertThat(service.getAtrPercentile90("BTC-USD")).contains(123.4);
    }
}
