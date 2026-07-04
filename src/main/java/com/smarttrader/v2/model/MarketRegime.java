package com.smarttrader.v2.model;

/**
 * Market regime as classified by MarketRegimeDetector.
 * Priority order when multiple conditions overlap: BREAKOUT > CONTINUATION > PULLBACK > PANIC > DISTRIBUTION.
 */
public enum MarketRegime {
    BREAKOUT,
    CONTINUATION,
    PULLBACK,
    PANIC,
    DISTRIBUTION
}
