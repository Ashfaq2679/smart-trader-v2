package com.smarttrader.v2.client;

import java.time.Duration;

/**
 * Coinbase Advanced Trade candle granularities.
 */
public enum Granularity {
    ONE_MINUTE("ONE_MINUTE", Duration.ofMinutes(1)),
    FIVE_MINUTE("FIVE_MINUTE", Duration.ofMinutes(5)),
    FIFTEEN_MINUTE("FIFTEEN_MINUTE", Duration.ofMinutes(15)),
    ONE_HOUR("ONE_HOUR", Duration.ofHours(1)),
    FOUR_HOUR("FOUR_HOUR", Duration.ofHours(4));

    private final String apiValue;
    private final Duration duration;

    Granularity(String apiValue, Duration duration) {
        this.apiValue = apiValue;
        this.duration = duration;
    }

    public String apiValue() {
        return apiValue;
    }

    /** Expected time between consecutive candles at this granularity. */
    public Duration duration() {
        return duration;
    }
}
