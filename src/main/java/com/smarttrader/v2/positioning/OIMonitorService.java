package com.smarttrader.v2.positioning;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.smarttrader.v2.constants.PositioningConstants;

import lombok.extern.slf4j.Slf4j;

/**
 * Open Interest (OI) monitor, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md section 1B.3.
 * Same fetchFromVenue-is-a-stub caveat as FundingMonitorService: real values need a
 * configured venue integration this codebase doesn't have yet.
 */
@Slf4j
@Service
public class OIMonitorService {

    @Value("${smart-trader.v2_5.tracked-symbols:}")
    private List<String> trackedSymbols;

    private final Map<String, Deque<OiSample>> oiHistory = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = PositioningConstants.OI_POLL_INTERVAL_MS)
    public void fetchOI() {
        String venue = "binance"; // TODO: config-selected per section 1
        Instant now = Instant.now();
        for (String symbol : trackedSymbols) {
            double oi = fetchFromVenue(venue, symbol);
            storeOi(symbol, oi, now);
        }
    }

    void storeOi(String symbol, double openInterest, Instant now) {
        Deque<OiSample> history = oiHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(new OiSample(openInterest, now));
            while (history.size() > PositioningConstants.OI_MAX_HISTORY) {
                history.pollFirst();
            }
        }
        log.info("oi stored symbol={} openInterest={}", symbol, openInterest);
    }

    /** Fractional change (0.15 = +15%) over the last 1 hour; 0.0 if there's no baseline sample yet. */
    public double getOIChange1h(String symbol) {
        return changeSince(symbol, Duration.ofHours(1));
    }

    /** Fractional change (0.15 = +15%) over the last 24 hours; 0.0 if there's no baseline sample yet. */
    public double getOIChange24h(String symbol) {
        return changeSince(symbol, Duration.ofHours(24));
    }

    private double changeSince(String symbol, Duration window) {
        Deque<OiSample> history = oiHistory.get(symbol);
        if (history == null || history.isEmpty()) {
            return 0.0;
        }
        OiSample latest;
        OiSample baseline;
        synchronized (history) {
            latest = history.peekLast();
            baseline = closestSampleBefore(history, latest.timestamp().minus(window));
        }
        if (baseline == null || baseline.value() == 0) {
            return 0.0;
        }
        return (latest.value() - baseline.value()) / baseline.value();
    }

    /** Latest sample at or before `cutoff`; null if every retained sample is more recent than that. */
    private OiSample closestSampleBefore(Deque<OiSample> history, Instant cutoff) {
        OiSample best = null;
        for (OiSample sample : history) {
            if (!sample.timestamp().isAfter(cutoff)) {
                best = sample;
            }
        }
        return best;
    }

    /** Stub: replace with a real REST call to the configured venue's open-interest endpoint. */
    private double fetchFromVenue(String venue, String symbol) {
        return 0.0;
    }

    private record OiSample(double value, Instant timestamp) {
    }
}
