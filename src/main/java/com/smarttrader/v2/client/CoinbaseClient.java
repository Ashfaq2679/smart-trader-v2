package com.smarttrader.v2.client;

import com.smarttrader.v2.model.Candle;

import java.time.Instant;
import java.util.List;

/**
 * Read-only market data access to Coinbase Advanced Trade API.
 * Strategies never call this directly; only services in the client/service layer do.
 */
public interface CoinbaseClient {

    List<Candle> getCandles(String productId, Granularity granularity);

    /**
     * Fetches only candles at or after {@code start} (inclusive), used by CandleCacheService
     * to incrementally extend an already-cached candle series instead of re-fetching the
     * full range every time.
     */
    List<Candle> getCandles(String productId, Granularity granularity, Instant start);
}
