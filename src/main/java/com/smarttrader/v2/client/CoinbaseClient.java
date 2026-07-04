package com.smarttrader.v2.client;

import com.smarttrader.v2.model.Candle;

import java.util.List;

/**
 * Read-only market data access to Coinbase Advanced Trade API.
 * Strategies never call this directly; only services in the client/service layer do.
 */
public interface CoinbaseClient {

    List<Candle> getCandles(String productId, Granularity granularity);
}
