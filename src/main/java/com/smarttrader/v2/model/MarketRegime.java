package com.smarttrader.v2.model;

/**
 * Market regime as classified by MarketRegimeDetector.
 *
 * v2.2 priority order when multiple conditions overlap:
 * BREAKOUT > CONTINUATION > PULLBACK > PANIC > DISTRIBUTION.
 *
 * v2.5 adds 5 regimes (V2_TECH_SPEC_v2.5.md section 2, Playbook Matrix); per the
 * incremental plan's Phase 0, they exist here but MarketRegimeDetector.detect() does not
 * yet classify into them - that detection logic lands in Phase 2.
 */
public enum MarketRegime {
    // --- v2.2 ---
    BREAKOUT,
    CONTINUATION,
    PULLBACK,
    PANIC,
    DISTRIBUTION,

    // --- v2.5 ---
    /** Bands wide, no trend, ADX < 25: the whale's accumulation/distribution lane. */
    RANGE,
    /** Toxic tape: spread > 8bps OR band < 1.5 x ATR. Stand aside. */
    CHOP,
    /** Market alarm / flash-crash: blanket 3-bar observation, then Cascade-Reversal. */
    NEWS_SHOCK,
    /** funding > 90th percentile AND OI up 15%: retail max-long, crowd is the fuel. */
    SQUEEZE_LONG,
    /** funding < 10th percentile AND OI up 15%: retail max-short, crowd is the fuel. */
    SQUEEZE_SHORT
}
