package com.smarttrader.v2.positioning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smarttrader.v2.constants.PositioningConstants;

import lombok.extern.slf4j.Slf4j;

/**
 * Perpetual funding rate monitor, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md section
 * 1B.2 / V2_TECH_SPEC_v2.5.md section 1: "read-only market intelligence even though
 * execution is spot" - never an execution trigger on its own (section 11).
 *
 * fetchFromVenue is a stub: real integration with a config-selected venue (Coinbase
 * International / Binance / Bybit) needs venue credentials and API contracts this
 * codebase doesn't have yet. Until then funding stays at its neutral default (0 rate,
 * 50th percentile) and callers degrade per section 11's Data Dependency Matrix
 * ("SQUEEZE regimes disabled; crowd multipliers = 1.0") rather than failing.
 */
@Slf4j
@Service
public class FundingMonitorService {

    @Value("${smart-trader.v2_5.tracked-symbols:}")
    private List<String> trackedSymbols;

    private final Map<String, Deque<Double>> fundingHistory = new ConcurrentHashMap<>();
    private final Cache<String, Integer> percentileCache = Caffeine.newBuilder()
            .expireAfterWrite(PositioningConstants.FUNDING_PERCENTILE_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
            .build();

    @Scheduled(fixedRate = PositioningConstants.FUNDING_POLL_INTERVAL_MS)
    public void fetchFunding() {
        String venue = "binance"; // TODO: config-selected per section 1
        for (String symbol : trackedSymbols) {
            double fundingRate = fetchFromVenue(venue, symbol);
            storeFunding(symbol, fundingRate);
        }
    }

    void storeFunding(String symbol, double fundingRateBps) {
        Deque<Double> history = fundingHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(fundingRateBps);
            while (history.size() > PositioningConstants.FUNDING_HISTORY_MAX_SAMPLES) {
                history.pollFirst();
            }
        }
        percentileCache.invalidate(symbol);
        log.info("funding stored symbol={} rateBps={}", symbol, fundingRateBps);
    }

    public double getCurrentFundingRateBps(String symbol) {
        Deque<Double> history = fundingHistory.get(symbol);
        if (history == null || history.isEmpty()) {
            return 0.0;
        }
        synchronized (history) {
            return history.peekLast();
        }
    }

    /** 0-100 percentile of the current rate within its own up-to-30-day history; 50 (neutral) with no data. */
    public int getFundingPercentile30d(String symbol) {
        Integer cached = percentileCache.getIfPresent(symbol);
        if (cached != null) {
            return cached;
        }
        int computed = computeFundingPercentile(symbol);
        percentileCache.put(symbol, computed);
        return computed;
    }

    private int computeFundingPercentile(String symbol) {
        Deque<Double> history = fundingHistory.get(symbol);
        if (history == null || history.isEmpty()) {
            return 50;
        }
        List<Double> snapshot;
        double current;
        synchronized (history) {
            snapshot = new ArrayList<>(history);
            current = history.peekLast();
        }
        long countAtOrBelow = snapshot.stream().filter(rate -> rate <= current).count();
        return (int) Math.round((countAtOrBelow * 100.0) / snapshot.size());
    }

    /** Stub: replace with a real REST call to the configured venue's funding-rate endpoint. */
    private double fetchFromVenue(String venue, String symbol) {
        return 0.0;
    }
}
