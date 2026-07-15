package com.smarttrader.v2.feedback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.constants.FeedbackConstants;
import com.smarttrader.v2.event.ConfigChangedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.ConfigChangeRecord;
import com.smarttrader.v2.service.ProductService;
import com.smarttrader.v2.util.CandleMath;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Nightly threshold-drift detection, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase
 * 5.2. Uses ProductService.getLiveCandles (real Coinbase candle history) rather than a
 * CandleRepository - this codebase doesn't persist candles, and re-fetching them here is
 * consistent with how ProductService is used everywhere else.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdDriftEstimator {

    private final ProductService productService;
    private final ThresholdConfigService configService;
    private final ConfigChangeRepository configChangeRepository;
    private final TradingEventPublisher eventPublisher;

    @Value("${smart-trader.v2_5.tracked-symbols:}")
    private List<String> trackedSymbols;

    @Async
    @Scheduled(cron = "0 0 2 * * *")
    public void estimateThresholdDrift() {
        for (String symbol : trackedSymbols) {
            estimateForSymbol(symbol);
        }
    }

    private void estimateForSymbol(String symbol) {
        List<Candle> candles = productService.getLiveCandles(symbol, Granularity.ONE_HOUR);
        List<Double> atrValues = rollingAtrSeries(candles);
        if (atrValues.isEmpty()) {
            return;
        }

        double newAtrPercentile90 = percentile(atrValues, FeedbackConstants.THRESHOLD_ATR_PERCENTILE);
        Optional<Double> prior = configService.getAtrPercentile90(symbol);

        if (prior.isEmpty()) {
            configService.updateAtrPercentile90(symbol, newAtrPercentile90);
            log.info("thresholdDrift symbol={} action=seeded atrPercentile90={}", symbol, newAtrPercentile90);
            return;
        }

        double oldValue = prior.get();
        if (oldValue != 0 && Math.abs(newAtrPercentile90 - oldValue) > oldValue * FeedbackConstants.THRESHOLD_DRIFT_FRACTION) {
            configService.updateAtrPercentile90(symbol, newAtrPercentile90);
            recordChange(symbol, oldValue, newAtrPercentile90);
            log.info("thresholdDrift symbol={} old={} new={} action=updated", symbol, oldValue, newAtrPercentile90);
        }
    }

    /** ATR at each bar over a rolling THRESHOLD_ATR_PERIOD window of true ranges. */
    private List<Double> rollingAtrSeries(List<Candle> candles) {
        int period = FeedbackConstants.THRESHOLD_ATR_PERIOD;
        List<Double> series = new ArrayList<>();
        if (candles.size() < period + 1) {
            return series;
        }

        List<Double> trueRanges = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            trueRanges.add(CandleMath.trueRange(candles.get(i), candles.get(i - 1)));
        }

        for (int i = period - 1; i < trueRanges.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += trueRanges.get(j);
            }
            series.add(sum / period);
        }
        return series;
    }

    private double percentile(List<Double> values, int p) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private void recordChange(String symbol, double oldValue, double newValue) {
        Instant now = Instant.now();
        ConfigChangeRecord record = ConfigChangeRecord.builder()
                .configKey("atr_percentile_90")
                .symbol(symbol)
                .oldValue(String.valueOf(oldValue))
                .newValue(String.valueOf(newValue))
                .reason("ATR p90 drifted more than " + (FeedbackConstants.THRESHOLD_DRIFT_FRACTION * 100) + "% vs prior config")
                .changedAtNs(System.nanoTime())
                .changedAt(now)
                .build();
        configChangeRepository.save(record);

        ConfigChangedEvent event = new ConfigChangedEvent();
        event.symbol = symbol;
        event.configKey = "atr_percentile_90";
        event.oldValue = String.valueOf(oldValue);
        event.newValue = String.valueOf(newValue);
        eventPublisher.publish(event);
    }
}
