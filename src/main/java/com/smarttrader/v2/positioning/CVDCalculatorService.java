package com.smarttrader.v2.positioning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.constants.PositioningConstants;
import com.smarttrader.v2.model.Match;

/**
 * Cumulative Volume Delta (CVD), per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md section
 * 1B.1 / V2_TECH_SPEC_v2.5.md section 1: running sum(buy-taker volume - sell-taker
 * volume) from the matches stream, plus its rolling slope for trend confirmation and
 * "new N-bar high" for divergence detection (section 4).
 */
@Service
public class CVDCalculatorService {

    private final Map<String, Double> cvdBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Deque<Double>> cvdHistory = new ConcurrentHashMap<>();

    /** Sums buy-taker minus sell-taker volume across the batch and updates the running CVD. */
    public void onMatchesStream(String symbol, List<Match> matches) {
        double deltaCvd = matches.stream()
                .mapToDouble(m -> "BUY".equalsIgnoreCase(m.takerSide()) ? m.volume() : -m.volume())
                .sum();

        double currentCvd = cvdBySymbol.merge(symbol, deltaCvd, Double::sum);

        Deque<Double> history = cvdHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(currentCvd);
            while (history.size() > PositioningConstants.CVD_MAX_HISTORY) {
                history.pollFirst();
            }
        }
    }

    public double getCVD1m(String symbol) {
        return cvdBySymbol.getOrDefault(symbol, 0.0);
    }

    /** Linear regression slope of CVD over the last CVD_SLOPE_WINDOW samples; 0 if not enough history yet. */
    public double getCVDSlope5m(String symbol) {
        List<Double> history = snapshot(symbol);
        if (history.size() < PositioningConstants.CVD_SLOPE_WINDOW) {
            return 0.0;
        }
        List<Double> window = history.subList(history.size() - PositioningConstants.CVD_SLOPE_WINDOW, history.size());
        return linearRegressionSlope(window);
    }

    /**
     * True if the current CVD is the highest value seen in the last `lookback` samples
     * (section 4: "breakout long valid only if cvd_1m made a new 20-bar high with price").
     * False (not "unknown") when there isn't enough history yet - callers should treat
     * that as "no confirmation", the conservative reading.
     */
    public boolean isNewHigh(String symbol, int lookback) {
        List<Double> history = snapshot(symbol);
        if (history.isEmpty()) {
            return false;
        }
        List<Double> window = history.subList(Math.max(0, history.size() - lookback), history.size());
        double max = window.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NEGATIVE_INFINITY);
        double current = history.get(history.size() - 1);
        return current >= max;
    }

    private List<Double> snapshot(String symbol) {
        Deque<Double> history = cvdHistory.get(symbol);
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    /** Ordinary least squares slope, x = 0..n-1 (bar index), y = CVD value. */
    private double linearRegressionSlope(List<Double> values) {
        int n = values.size();
        if (n < 2) {
            return 0.0;
        }
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = values.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denominator = n * sumXX - sumX * sumX;
        if (denominator == 0) {
            return 0.0;
        }
        return (n * sumXY - sumX * sumY) / denominator;
    }
}
