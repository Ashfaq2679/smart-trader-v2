package com.smarttrader.v2.risk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class PositionSizingTest {

    private final PositionSizing positionSizing = new PositionSizing();

    @Test
    void bullish_longPositionSizeIsRiskAmountOverStopDistance() {
        double result = positionSizing.calculate(10_000, 0.01, 100.0, 95.0);

        assertThat(result).isCloseTo(100.0 / 5.0, offset(0.0001));
    }

    @Test
    void bearish_shortPositionSizeUsesAbsoluteStopDistance() {
        double result = positionSizing.calculate(10_000, 0.01, 95.0, 100.0);

        assertThat(result).isCloseTo(100.0 / 5.0, offset(0.0001));
    }

    @Test
    void sideways_zeroRiskPercentProducesZeroPositionSize() {
        double result = positionSizing.calculate(10_000, 0.0, 100.0, 95.0);

        assertThat(result).isZero();
    }

    @Test
    void edgeCase_entryEqualsStopReturnsZeroToAvoidDivisionByZero() {
        double result = positionSizing.calculate(10_000, 0.01, 100.0, 100.0);

        assertThat(result).isZero();
    }
}
