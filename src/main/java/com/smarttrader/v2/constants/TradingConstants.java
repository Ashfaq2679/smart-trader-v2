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

    /**
     * Position Service Enhancements (v1.1 section 7): force-exit a position once its
     * unrealized loss reaches this multiple of the original per-unit risk (|entry - stop|).
     */
    public static final double UNREALIZED_LOSS_GUARD_RISK_MULTIPLIER = 1.5;

    /**
     * Portfolio Risk Controls (v1.1 section 8). None of these thresholds are given exact
     * values by the spec; documented assumptions chosen to be conservative defaults:
     */
    /** Cap total open notional exposure at this fraction of capital. */
    public static final double MAX_PORTFOLIO_EXPOSURE_PERCENT = 0.20;
    /** Rolling window size (number of returns) used to compute correlation between products. */
    public static final int CORRELATION_WINDOW_SIZE = 30;
    /** Minimum overlapping return samples required before a correlation is considered reliable. */
    public static final int CORRELATION_MIN_SAMPLES = 5;
    /** Absolute correlation above which two products are treated as concentrated risk. */
    public static final double CORRELATION_THRESHOLD = 0.7;
    /** Multiplicative size reduction applied per highly-correlated open position. */
    public static final double CORRELATION_SIZE_REDUCTION_FACTOR = 0.5;
    /** Floor so correlation adjustment never reduces size to (near) zero outright. */
    public static final double MIN_CORRELATION_MULTIPLIER = 0.25;

    /**
     * Data Integrity (v1.1 section 10). Exact thresholds aren't given by the spec;
     * documented assumptions. MAX_DATA_LATENCY_MS is deliberately generous (5 minutes,
     * not seconds): dataLatencyMs is derived from candle close time, and even a fresh
     * 1-hour candle can legitimately be tens of seconds to a few minutes old by the time
     * it's fetched and processed - this should catch a genuinely stalled feed, not normal
     * candle-based polling latency.
     */
    public static final long MAX_DATA_LATENCY_MS = 300_000;
    /** Flag a candle-to-candle open/close move larger than this fraction as a price gap. */
    public static final double MAX_PRICE_GAP_PERCENT = 0.10;

    /** volumeSpike lookback/multiplier, per V2_TECH_SPEC_v1.1.md section 1: "volumeSpike (20, 1.8)". */
    public static final int VOLUME_SPIKE_LOOKBACK = 20;
    public static final double VOLUME_SPIKE_MULTIPLIER = 1.8;

    /** strongCandle threshold, per V2_TECH_SPEC_v1.1.md section 1: "body/range >= 0.6". */
    public static final double STRONG_CANDLE_BODY_RATIO = 0.6;

    /** atrSpike isn't given a threshold by the spec; current true range vs this multiple of ATR. */
    public static final double ATR_SPIKE_MULTIPLIER = 1.5;

    private TradingConstants() {
    }
}
