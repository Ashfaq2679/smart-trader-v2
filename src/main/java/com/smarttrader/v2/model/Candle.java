package com.smarttrader.v2.model;

import lombok.Builder;

import java.time.Instant;

/**
 * Immutable OHLCV candle.
 */
@Builder
public record Candle(
        Instant timestamp,
        double open,
        double high,
        double low,
        double close,
        double volume
) {
}
