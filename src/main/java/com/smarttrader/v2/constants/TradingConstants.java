package com.smarttrader.v2.constants;

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

    private TradingConstants() {
    }
}
