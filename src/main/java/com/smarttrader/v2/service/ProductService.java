package com.smarttrader.v2.service;

import com.smarttrader.v2.client.CoinbaseClient;
import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves live candle data for a product across all tracked timeframes.
 * Persistence is out of scope here; this is read-through market data access only.
 *
 * Uses CoinbaseClient directly (the public, unauthenticated candle endpoint) rather
 * than CoinbaseClientFactory, which builds per-user authenticated clients for trading.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final CoinbaseClient coinbaseClient;

    /**
     * Fetches live candles for the given product across every tracked granularity
     * (1m, 5m, 15m, 1h, 4h), matching the candles.* Kafka topics in the tech spec.
     */
    public Map<Granularity, List<Candle>> getAllLiveCandles(String productId) {
        Map<Granularity, List<Candle>> candlesByGranularity = new EnumMap<>(Granularity.class);
        for (Granularity granularity : Granularity.values()) {
            candlesByGranularity.put(granularity, coinbaseClient.getCandles(productId, granularity));
        }
        return candlesByGranularity;
    }

    public List<Candle> getLiveCandles(String productId, Granularity granularity) {
        return coinbaseClient.getCandles(productId, granularity);
    }
}
