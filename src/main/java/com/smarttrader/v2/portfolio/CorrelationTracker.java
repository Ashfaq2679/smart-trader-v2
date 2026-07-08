package com.smarttrader.v2.portfolio;

import com.smarttrader.v2.constants.TradingConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling correlation matrix, per V2_TECH_SPEC_v1.1.md section 8 ("Dynamic correlation
 * matrix (rolling)"). Rather than eagerly maintaining a full NxN matrix (most pairs are
 * never queried), this keeps a rolling window of per-product returns and computes Pearson
 * correlation for a pair only when asked - O(N) per query on the window size, no
 * redundant recalculation, per section 13's execution constraints.
 */
@Component
public class CorrelationTracker {

    private final Map<String, Deque<Double>> returnsByProduct = new ConcurrentHashMap<>();
    private final Map<String, Double> lastPriceByProduct = new ConcurrentHashMap<>();

    /** Feeds a new price observation for productId, deriving and storing its return. */
    public void recordPrice(String productId, double price) {
        Double lastPrice = lastPriceByProduct.put(productId, price);
        if (lastPrice == null || lastPrice <= 0) {
            return;
        }
        double returnPct = (price - lastPrice) / lastPrice;
        Deque<Double> window = returnsByProduct.computeIfAbsent(productId, id -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(returnPct);
            while (window.size() > TradingConstants.CORRELATION_WINDOW_SIZE) {
                window.pollFirst();
            }
        }
    }

    /**
     * Pearson correlation between the two products' most recent overlapping returns.
     * Empty if productA equals productB is not the case being asked, or there isn't
     * enough overlapping history yet (CORRELATION_MIN_SAMPLES).
     */
    public Optional<Double> correlation(String productA, String productB) {
        if (productA.equals(productB)) {
            return Optional.of(1.0);
        }

        List<Double> a = snapshot(productA);
        List<Double> b = snapshot(productB);
        int n = Math.min(a.size(), b.size());
        if (n < TradingConstants.CORRELATION_MIN_SAMPLES) {
            return Optional.empty();
        }

        List<Double> aTail = a.subList(a.size() - n, a.size());
        List<Double> bTail = b.subList(b.size() - n, b.size());
        return Optional.of(pearson(aTail, bTail));
    }

    private List<Double> snapshot(String productId) {
        Deque<Double> window = returnsByProduct.get(productId);
        if (window == null) {
            return List.of();
        }
        synchronized (window) {
            return new ArrayList<>(window);
        }
    }

    private double pearson(List<Double> x, List<Double> y) {
        int n = x.size();
        double meanX = mean(x);
        double meanY = mean(y);

        double covariance = 0;
        double varianceX = 0;
        double varianceY = 0;
        for (int i = 0; i < n; i++) {
            double dx = x.get(i) - meanX;
            double dy = y.get(i) - meanY;
            covariance += dx * dy;
            varianceX += dx * dx;
            varianceY += dy * dy;
        }

        double denominator = Math.sqrt(varianceX * varianceY);
        if (denominator == 0) {
            return 0;
        }
        return covariance / denominator;
    }

    private double mean(List<Double> values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }
}
