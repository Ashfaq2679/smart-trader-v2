package com.smarttrader.v2.model;

import com.smarttrader.v2.client.Granularity;

/**
 * Cache key for a product's candle series at a given granularity, used by CandleCacheService.
 */
public record CandleCacheKey(String productId, Granularity granularity) {
}
