package com.smarttrader.v2.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.CoinDocument;
import com.smarttrader.v2.repository.ProductsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retrieves live candle data for a product across all tracked timeframes.
 * Persistence is out of scope here; this is read-through market data access only.
 *
 * Uses CandleCacheService rather than CoinbaseClient directly, so repeated calls
 * only fetch new candles since the last cached timestamp instead of the full range.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final CandleCacheService candleCacheService;
    private final ProductsRepository productsRepository;
	@Value("${candles.ignore.names:BTC-USDC,ETH-USDC}")
	private List<String> ignoreProductIds;


    /**
     * Fetches live candles for the given product across every tracked granularity
     * (1m, 5m, 15m, 1h, 4h), matching the candles.* Kafka topics in the tech spec.
     */
    public Map<Granularity, List<Candle>> getAllLiveCandles(String productId) {
        Map<Granularity, List<Candle>> candlesByGranularity = new EnumMap<>(Granularity.class);
        for (Granularity granularity : Granularity.values()) {
            candlesByGranularity.put(granularity, candleCacheService.getCandles(productId, granularity));
        }
        return candlesByGranularity;
    }

    public List<Candle> getLiveCandles(String productId, Granularity granularity) {
        return candleCacheService.getCandles(productId, granularity);
    }
    
    public List<String> findProductIdToProcess() {
		log.info("Finding product IDs to process, ignoring: {}", ignoreProductIds);
		List<String> productIds = productsRepository.findAll().stream()
				.filter(product -> product.getProductId() != null
								&& (ignoreProductIds == null 
								|| !ignoreProductIds.contains(product.getProductId())))
				.map(product -> product.getProductId())
				.toList();
		return productIds;
	}
}
