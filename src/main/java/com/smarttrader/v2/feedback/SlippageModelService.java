package com.smarttrader.v2.feedback;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Holds the current per-symbol slippage calibration factor, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5.1.
 *
 * TradeOutcome.slippageMultiple is already expressed as "actual / modeled" (Phase 4.4),
 * so the value tracked here is a calibration factor starting at 1.0 (trust the original
 * fill-cost model) - when realized fills consistently run at N times worse than that
 * factor already accounts for, SlippageCalibrator folds N into this factor going forward.
 *
 * In-memory only: this codebase has no persistent ConfigurationService yet. The *change*
 * to this value is still durably audited via ConfigChangeRecord, so nothing about a
 * recalibration is lost on restart even though the live value itself resets to default.
 */
@Service
public class SlippageModelService {

    private final ConcurrentHashMap<String, AtomicReference<Double>> factorsBySymbol = new ConcurrentHashMap<>();
    private final double defaultFactor;

    public SlippageModelService(@Value("${smart-trader.v2_5.feedback.default-slippage-factor:1.0}") double defaultFactor) {
        this.defaultFactor = defaultFactor;
    }

    public double getModeledSlippage(String symbol) {
        AtomicReference<Double> ref = factorsBySymbol.get(symbol);
        return ref != null ? ref.get() : defaultFactor;
    }

    public void updateSlippageModel(String symbol, double newFactor) {
        factorsBySymbol.computeIfAbsent(symbol, s -> new AtomicReference<>(defaultFactor)).set(newFactor);
    }
}
