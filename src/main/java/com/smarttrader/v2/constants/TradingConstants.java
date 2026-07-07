package com.smarttrader.v2.constants;

import java.time.Duration;

/**
 * Shared numeric constants used by regime detection and strategy evaluation,
 * so the same rule (e.g. "near EMA/support", "consolidation threshold") is
 * defined once per the "no duplicated calculations" coding standard.
 */
public final class TradingConstants {

    /** Consolidation range must be below 2% for continuation, per spec. */
    public static final double CONTINUATION_CONSOLIDATION_THRESHOLD = 0.02;

    /** "near" EMA50/support = within 0.5 ATR, since spec doesn't give a fixed percent. */
    public static final double NEAR_ATR_MULTIPLIER = 0.5;

    /** Pullback stop buffer below support, per spec: support - ATR * 0.5. */
    public static final double PULLBACK_STOP_ATR_BUFFER = 0.5;

    /** Breakout stop distance, per spec: ATR * 1.2. */
    public static final double BREAKOUT_RISK_ATR = 1.2;

    /** Breakout target distance, per spec: ATR * 3.0. */
    public static final double BREAKOUT_REWARD_ATR = 3.0;

    /** Continuation target extension when no prior measured move is available. */
    public static final double CONTINUATION_TARGET_ATR = 2.0;

    /** Minimum acceptable risk:reward ratio, per spec section 4. */
    public static final double MIN_RISK_REWARD = 2.0;

    /**
     * Default per-trade fees/slippage used by the effective R:R filter (v1.1 section 4)
     * when the caller doesn't supply venue-specific values. Zero preserves the raw R:R
     * behavior until real cost estimates are wired in.
     */
    public static final double DEFAULT_FEES = 0.0;
    public static final double DEFAULT_SLIPPAGE = 0.0;

    /**
     * How long a strategy's signal remains actionable before it must be re-evaluated
     * (v1.1 section 3 "validityWindow"). The spec does not give exact durations, so
     * these follow each strategy's typical holding horizon: breakouts are time-critical,
     * pullbacks and continuations tolerate a wider entry window.
     */
    public static final Duration PULLBACK_VALIDITY_WINDOW = Duration.ofMinutes(15);
    public static final Duration BREAKOUT_VALIDITY_WINDOW = Duration.ofMinutes(5);
    public static final Duration CONTINUATION_VALIDITY_WINDOW = Duration.ofMinutes(10);

    /**
     * Adaptive adjustment per v1.1 section 3.1: on an ATR spike (high volatility)
     * shrink the validity window since price can invalidate the setup faster;
     * otherwise (low volatility) extend it slightly since the setup stays actionable longer.
     */
    public static final double VALIDITY_HIGH_VOLATILITY_FACTOR = 0.5;
    public static final double VALIDITY_LOW_VOLATILITY_FACTOR = 1.2;

    /** Applies the section 3.1 adaptive adjustment to a strategy's base validity window. */
    public static Duration adjustedValidityWindow(Duration baseWindow, boolean atrSpike) {
        double factor = atrSpike ? VALIDITY_HIGH_VOLATILITY_FACTOR : VALIDITY_LOW_VOLATILITY_FACTOR;
        return Duration.ofMillis((long) (baseWindow.toMillis() * factor));
    }

    /**
     * Order execution realism (v1.1 section 6): cancel an order if the quoted price has
     * moved more than this fraction away from the signal's entry price. The spec doesn't
     * give an exact number, so 0.5% is used as a conservative default for crypto majors.
     */
    public static final double SLIPPAGE_TOLERANCE = 0.005;

    private TradingConstants() {
    }
}
