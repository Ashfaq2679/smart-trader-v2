package com.smarttrader.v2.validation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.model.ShadowSignal;
import com.smarttrader.v2.model.SignalResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Intercepts signals from RESEARCH/SHADOW-stage strategies without placing orders, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowModeService {

    private static final int LOOKBACK_DAYS = 28;

    private final ShadowSignalRepository repository;

    public void logShadowSignal(String strategyName, String symbol, SignalResult signal) {
        Instant now = Instant.now();
        ShadowSignal shadow = ShadowSignal.builder()
                .strategyName(strategyName)
                .symbol(symbol)
                .direction(signal.direction().name())
                .entry(signal.entry())
                .stop(signal.stop())
                .target(signal.target())
                .riskReward(signal.riskReward())
                .valid(signal.valid())
                .detectedAtNs(System.nanoTime())
                .detectedAt(now)
                .build();

        repository.save(shadow);
        log.info("shadowSignal strategy={} symbol={} direction={} valid={}",
                strategyName, symbol, shadow.getDirection(), shadow.isValid());
    }

    public ShadowMetrics getMetrics(String strategyName, String symbol) {
        Instant since = Instant.now().minus(LOOKBACK_DAYS, java.time.temporal.ChronoUnit.DAYS);
        List<ShadowSignal> signals = repository
                .findByStrategyNameAndSymbolAndDetectedAtAfterOrderByDetectedAtAsc(strategyName, symbol, since);

        if (signals.isEmpty()) {
            return ShadowMetrics.empty();
        }

        Duration age = Duration.between(signals.get(0).getDetectedAt(), Instant.now());
        return new ShadowMetrics(age, signals.size(), 0.0);
    }
}
