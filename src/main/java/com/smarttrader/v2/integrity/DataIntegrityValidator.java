package com.smarttrader.v2.integrity;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Data Integrity, per V2_TECH_SPEC_v1.1.md section 10:
 * - Reject stale data
 * - Ensure candle sequence continuity
 * - Validate price gaps
 *
 * Each check is a standalone, O(N) method (section 13: no redundant recalculation, O(N)
 * only) so callers can run only the checks relevant to what they have on hand. Candle
 * lists must already be sorted ascending by timestamp (CandleCacheService's output already
 * satisfies this).
 */
@Slf4j
@Component
public class DataIntegrityValidator {

    /**
     * Rejects an AnalysisContext whose data is too old to trade on.
     *
     * @throws DataIntegrityException if ctx.dataLatencyMs() exceeds MAX_DATA_LATENCY_MS
     */
    public void rejectStaleData(AnalysisContext ctx) {
        if (ctx.dataLatencyMs() > TradingConstants.MAX_DATA_LATENCY_MS) {
            throw new DataIntegrityException(DataIntegrityViolationType.STALE_DATA,
                    "data latency %dms exceeds maximum %dms".formatted(ctx.dataLatencyMs(), TradingConstants.MAX_DATA_LATENCY_MS));
        }
    }

    /**
     * Ensures consecutive candles are exactly one granularity interval apart: no gaps,
     * no duplicates, no out-of-order entries.
     *
     * @throws DataIntegrityException on the first non-contiguous pair found
     */
    public void ensureCandleSequenceContinuity(List<Candle> candles, Granularity granularity) {
        Duration expected = granularity.duration();
        for (int i = 1; i < candles.size(); i++) {
            Duration actual = Duration.between(candles.get(i - 1).timestamp(), candles.get(i).timestamp());
            if (!actual.equals(expected)) {
                throw new DataIntegrityException(DataIntegrityViolationType.SEQUENCE_GAP,
                        "candle sequence gap between %s and %s: expected %s apart, was %s"
                                .formatted(candles.get(i - 1).timestamp(), candles.get(i).timestamp(), expected, actual));
            }
        }
    }

    /**
     * Flags an abnormally large price move between one candle's close and the next
     * candle's open.
     *
     * @throws DataIntegrityException on the first gap exceeding MAX_PRICE_GAP_PERCENT
     */
    public void validatePriceGaps(List<Candle> candles) {
        for (int i = 1; i < candles.size(); i++) {
            double previousClose = candles.get(i - 1).close();
            double currentOpen = candles.get(i).open();
            if (previousClose == 0) {
                continue;
            }
            double gapPercent = Math.abs(currentOpen - previousClose) / previousClose;
            if (gapPercent > TradingConstants.MAX_PRICE_GAP_PERCENT) {
                throw new DataIntegrityException(DataIntegrityViolationType.PRICE_GAP,
                        "price gap of %.4f between candle close %.8f and next open %.8f exceeds maximum %.4f"
                                .formatted(gapPercent, previousClose, currentOpen, TradingConstants.MAX_PRICE_GAP_PERCENT));
            }
        }
    }
}
