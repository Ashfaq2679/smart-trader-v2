package com.smarttrader.v2.feedback;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Holds the current per-symbol ATR-p90 threshold, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5.2.
 *
 * In-memory only, same rationale as SlippageModelService: no persistent
 * ConfigurationService exists yet, but every change is durably audited via
 * ConfigChangeRecord regardless. Returns Optional (not a defaulted primitive) because,
 * unlike slippage, there's no sane cross-symbol default ATR magnitude (BTC's ATR is
 * orders of magnitude larger than a low-price altcoin's) - the first observation for a
 * symbol seeds the value rather than being compared against a made-up baseline.
 */
@Service
public class ThresholdConfigService {

    private final ConcurrentHashMap<String, Double> atrPercentile90BySymbol = new ConcurrentHashMap<>();

    public Optional<Double> getAtrPercentile90(String symbol) {
        return Optional.ofNullable(atrPercentile90BySymbol.get(symbol));
    }

    public void updateAtrPercentile90(String symbol, double value) {
        atrPercentile90BySymbol.put(symbol, value);
    }
}
