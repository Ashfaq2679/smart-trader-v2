package com.smarttrader.v2.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.smarttrader.v2.client.CoinbaseClient;
import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.event.CandleUpdatedEvent;
import com.smarttrader.v2.event.DomainEventPublisher;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.CandleCacheKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps a growing, cached candle series per (productId, granularity) and only fetches
 * the delta from Coinbase since the last cached candle's timestamp, instead of
 * re-fetching the full range on every call.
 *
 * Publishes a CandleUpdatedEvent (section 9) for every newly-seen candle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleCacheService {

    private final CoinbaseClient coinbaseClient;
    private final Cache<CandleCacheKey, List<Candle>> candleCache;
    private final DomainEventPublisher eventPublisher;

    public List<Candle> getCandles(String productId, Granularity granularity) {
        CandleCacheKey key = new CandleCacheKey(productId, granularity);
        return candleCache.asMap().compute(key, (k, cached) -> fetchAndMerge(productId, granularity, cached));
    }

    private List<Candle> fetchAndMerge(String productId, Granularity granularity, List<Candle> cached) {
        if (cached == null || cached.isEmpty()) {
            List<Candle> fetched = sortedCopy(coinbaseClient.getCandles(productId, granularity));
            log.info("candleCache productId={} granularity={} coldFetch candles={}", productId, granularity, fetched.size());
            publishCandleUpdated(productId, granularity, fetched);
            return fetched;
        }

        Instant lastTimestamp = cached.get(cached.size() - 1).timestamp();
        List<Candle> fresh = coinbaseClient.getCandles(productId, granularity, lastTimestamp.plusSeconds(1));
        List<Candle> merged = merge(cached, fresh);
        log.info("candleCache productId={} granularity={} since={} newCandles={} total={}",
                productId, granularity, lastTimestamp, fresh.size(), merged.size());
        publishCandleUpdated(productId, granularity, fresh);
        return merged;
    }

    private void publishCandleUpdated(String productId, Granularity granularity, List<Candle> newCandles) {
        newCandles.forEach(candle -> eventPublisher.publish(CandleUpdatedEvent.of(productId, granularity, candle)));
    }

    private static List<Candle> sortedCopy(List<Candle> candles) {
        return candles.stream().sorted(Comparator.comparing(Candle::timestamp)).toList();
    }

    private static List<Candle> merge(List<Candle> cached, List<Candle> fresh) {
        if (fresh.isEmpty()) {
            return cached;
        }
        Map<Instant, Candle> byTimestamp = new LinkedHashMap<>();
        cached.forEach(candle -> byTimestamp.put(candle.timestamp(), candle));
        fresh.forEach(candle -> byTimestamp.put(candle.timestamp(), candle));
        return byTimestamp.values().stream().sorted(Comparator.comparing(Candle::timestamp)).toList();
    }
}
