package com.smarttrader.v2.strategy;

import org.assertj.core.data.Offset;

final class TestOffsets {

    static final Offset<Double> DOUBLE_OFFSET = Offset.offset(0.0001);

    private TestOffsets() {
    }
}
