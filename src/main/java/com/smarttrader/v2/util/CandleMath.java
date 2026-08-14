package com.smarttrader.v2.util;

import com.smarttrader.v2.model.Candle;

/**
 * Shared candle math used by both AnalysisContextBuilder (live indicator snapshot) and
 * feedback-loop jobs (Phase 5) that need the same true-range formula over historical
 * candles, per the project rule against duplicated calculations.
 */
public final class CandleMath {

    public static double trueRange(Candle current, Candle previous) {
        double highLow = current.high() - current.low();
        double highPrevClose = Math.abs(current.high() - previous.close());
        double lowPrevClose = Math.abs(current.low() - previous.close());
        return Math.max(highLow, Math.max(highPrevClose, lowPrevClose));
    }

    private CandleMath() {
    }
}
