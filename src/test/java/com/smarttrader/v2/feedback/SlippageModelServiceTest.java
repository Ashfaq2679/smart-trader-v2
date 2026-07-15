package com.smarttrader.v2.feedback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlippageModelServiceTest {

    private final SlippageModelService service = new SlippageModelService(1.0);

    @Test
    void bullish_unseenSymbolReturnsDefaultFactor() {
        assertThat(service.getModeledSlippage("BTC-USD")).isEqualTo(1.0);
    }

    @Test
    void bearish_updateChangesFactorForThatSymbolOnly() {
        service.updateSlippageModel("BTC-USD", 2.5);

        assertThat(service.getModeledSlippage("BTC-USD")).isEqualTo(2.5);
        assertThat(service.getModeledSlippage("ETH-USD")).isEqualTo(1.0);
    }
}
