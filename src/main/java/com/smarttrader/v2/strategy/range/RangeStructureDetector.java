package com.smarttrader.v2.strategy.range;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;

/**
 * Detects a horizontal range surrounding current price, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL_WITH_RANGE_SR.md §2.3.1 lines 757-772.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Anchor the range at {@link AnalysisContext#nearestSupport()} and
 *       {@link AnalysisContext#nearestResistance()} - the two boundaries the rest of the
 *       system already agrees on.</li>
 *   <li>If a candle history is provided: find swing lows/highs (3-bar pivots), cluster
 *       touches within {@code boundaryToleranceAtr * ATR} of each boundary, and score
 *       horizontality by the standard deviation of touch prices.</li>
 *   <li>Reject when width &lt; minimumWidthAtr * ATR, or fewer than minimumTouches on
 *       either side.</li>
 * </ol>
 *
 * <p><b>Integration seam:</b> {@link AnalysisContext} does not yet carry a candle series.
 * Callers that have access to one should invoke
 * {@link #detect(AnalysisContext, List, RangeSupportResistanceConfig)}. The overload
 * {@link #detect(AnalysisContext, RangeSupportResistanceConfig)} degrades gracefully -
 * it returns a range built from the two AnalysisContext boundaries with touch/horizontality
 * information unavailable, which the strategy will then reject via its quality-score
 * threshold. This keeps the feature testable in isolation without modifying
 * AnalysisContext.
 */
@Component
public class RangeStructureDetector {

    /** Overload for callers with no candle history (returns a low-quality range that the strategy will reject). */
    public RangeStructure detect(AnalysisContext ctx, RangeSupportResistanceConfig config) {
        return detect(ctx, List.of(), config);
    }

    public RangeStructure detect(AnalysisContext ctx, List<Candle> candles, RangeSupportResistanceConfig config) {
        double atr = ctx.atr();
        double supportPrice = ctx.nearestSupport();
        double resistancePrice = ctx.nearestResistance();

        if (atr <= 0 || supportPrice <= 0 || resistancePrice <= 0 || resistancePrice <= supportPrice) {
            return RangeStructure.invalid();
        }

        double width = resistancePrice - supportPrice;
        double widthAtrMultiple = width / atr;

        if (widthAtrMultiple < config.minimumWidthAtr()) {
            return RangeStructure.invalid();
        }

        double tolerance = config.boundaryToleranceAtr() * atr;

        List<Double> supportTouches = findTouches(candles, supportPrice, tolerance, true);
        List<Double> resistanceTouches = findTouches(candles, resistancePrice, tolerance, false);

        int sTouchCount = supportTouches.size();
        int rTouchCount = resistanceTouches.size();

        // When no candle history is available at all, we cannot count touches. Build a
        // structure with 0 touches, low quality score - the strategy will reject via the
        // minimumRangeQuality gate. This keeps the pipeline testable while the candle
        // source is not yet wired.
        if (candles == null || candles.isEmpty()) {
            RangeBoundary support = new RangeBoundary(supportPrice - tolerance, supportPrice + tolerance, supportPrice, 0, 0);
            RangeBoundary resistance = new RangeBoundary(resistancePrice - tolerance, resistancePrice + tolerance, resistancePrice, 0, 0);
            return new RangeStructure(support, resistance, width, widthAtrMultiple, 0, false);
        }

        boolean touchesOk = sTouchCount >= config.minimumTouches() && rTouchCount >= config.minimumTouches();

        double horizontality = horizontalityScore(supportTouches, resistanceTouches, tolerance);
        double quality = qualityScore(sTouchCount, rTouchCount, widthAtrMultiple, horizontality, config);

        RangeBoundary support = new RangeBoundary(
                supportPrice - tolerance, supportPrice + tolerance, supportPrice,
                sTouchCount, strength(sTouchCount, config));
        RangeBoundary resistance = new RangeBoundary(
                resistancePrice - tolerance, resistancePrice + tolerance, resistancePrice,
                rTouchCount, strength(rTouchCount, config));

        boolean valid = touchesOk && widthAtrMultiple >= config.minimumWidthAtr();
        return new RangeStructure(support, resistance, width, widthAtrMultiple, quality, valid);
    }

    /**
     * Simple 3-bar pivot scan: for supports, a bar whose low is the local minimum among
     * itself and its two neighbours; symmetric for resistance highs. Then keep only
     * pivots within {@code tolerance} of {@code level}.
     */
    private List<Double> findTouches(List<Candle> candles, double level, double tolerance, boolean support) {
        List<Double> touches = new ArrayList<>();
        if (candles == null || candles.size() < 3) {
            return touches;
        }
        for (int i = 1; i < candles.size() - 1; i++) {
            Candle prev = candles.get(i - 1);
            Candle cur = candles.get(i);
            Candle next = candles.get(i + 1);
            if (support) {
                if (cur.low() <= prev.low() && cur.low() <= next.low()
                        && Math.abs(cur.low() - level) <= tolerance) {
                    touches.add(cur.low());
                }
            } else {
                if (cur.high() >= prev.high() && cur.high() >= next.high()
                        && Math.abs(cur.high() - level) <= tolerance) {
                    touches.add(cur.high());
                }
            }
        }
        return touches;
    }

    /** 0-100: higher means the collected touches sit tightly around the anchor level. */
    private double horizontalityScore(List<Double> supportTouches, List<Double> resistanceTouches, double tolerance) {
        double sStd = stddev(supportTouches);
        double rStd = stddev(resistanceTouches);
        if (tolerance <= 0) return 0;
        double sScore = Math.max(0, 100 * (1.0 - sStd / tolerance));
        double rScore = Math.max(0, 100 * (1.0 - rStd / tolerance));
        return (sScore + rScore) / 2.0;
    }

    private double stddev(List<Double> xs) {
        if (xs.isEmpty()) return Double.POSITIVE_INFINITY;
        double mean = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = xs.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0);
        return Math.sqrt(var);
    }

    /** Weighted blend of touch counts, width, and horizontality. */
    private double qualityScore(int sTouches, int rTouches, double widthAtrMultiple,
                                double horizontality, RangeSupportResistanceConfig config) {
        double touchScore = 100.0
                * Math.min(1.0, (double) sTouches / config.preferredTouches())
                * Math.min(1.0, (double) rTouches / config.preferredTouches());
        double widthScore = 100.0 * Math.min(1.0, widthAtrMultiple / (2.0 * config.minimumWidthAtr()));
        return 0.5 * touchScore + 0.3 * horizontality + 0.2 * widthScore;
    }

    private double strength(int touches, RangeSupportResistanceConfig config) {
        return 100.0 * Math.min(1.0, (double) touches / config.preferredTouches());
    }
}
