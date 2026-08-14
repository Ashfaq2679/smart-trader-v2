package com.smarttrader.v2.client;

/**
 * Coinbase Advanced Trade candle granularities.
 */
public enum Granularity {
    ONE_MINUTE("ONE_MINUTE"),
    FIVE_MINUTE("FIVE_MINUTE"),
    FIFTEEN_MINUTE("FIFTEEN_MINUTE"),
    THIRTY_MINUTE("THIRTY_MINUTE"),
    ONE_HOUR("ONE_HOUR"),
    FOUR_HOUR("FOUR_HOUR");

    private final String apiValue;

    Granularity(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
