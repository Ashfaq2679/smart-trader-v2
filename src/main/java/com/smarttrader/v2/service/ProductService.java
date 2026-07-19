package com.smarttrader.v2.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smarttrader.v2.client.CoinbaseClient;
import com.smarttrader.v2.client.CoinbaseClientFactory;
import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retrieves live candle data for a product across all tracked timeframes.
 * Persistence is out of scope here; this is read-through market data access only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final CoinbaseClientFactory coinbaseClientFactory;
    private final ProductRepository productRepository;

    /**
     * Fetches live candles for the given product across every tracked granularity
     * (1m, 5m, 15m, 1h, 4h), matching the candles.* Kafka topics in the tech spec.
     */
    public Map<Granularity, List<Candle>> getAllLiveCandles(String productId) {
        CoinbaseClient client = coinbaseClientFactory.create();
        Map<Granularity, List<Candle>> candlesByGranularity = new EnumMap<>(Granularity.class);
        for (Granularity granularity : Granularity.values()) {
            candlesByGranularity.put(granularity, client.getCandles(productId, granularity));
        }
        return candlesByGranularity;
    }

    public List<Candle> getLiveCandles(String productId, Granularity granularity) {
        return coinbaseClientFactory.create().getCandles(productId, granularity);
    }
    
    @Value("${candles.ignore.names:BTC-USDC,ETH-USDC}")
	private List<String> ignoreProductIds;

//	@Cacheable(cacheNames = "productIdsToProcess", key = "'productIds'")
	public List<String> findProductIdToProcess() {
		log.info("Finding product IDs to process, ignoring: {}", ignoreProductIds);
		List<String> productIds = productRepository.findAll().stream()
				.filter(coin -> coin.productId() != null
						&& (ignoreProductIds == null || !ignoreProductIds.contains(coin.productId())))
				.map(coin -> coin.productId()).toList();
		return productIds;
	}
}
