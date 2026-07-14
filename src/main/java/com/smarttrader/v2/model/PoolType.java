package com.smarttrader.v2.model;

/**
 * Liquidity pool classification, per V2_TECH_SPEC_v2.5.md section 3 (Liquidity Map).
 */
public enum PoolType {
    /** Equal Highs: >= 2 fractal highs within 0.15 * ATR of each other. */
    EQH,
    /** Equal Lows: >= 2 fractal lows within 0.15 * ATR of each other. */
    EQL,
    /** Session extreme: prior day/week, Asia/EU/US session high or low. */
    SESSION,
    /** Round number level (e.g. multiples of $1,000 for BTC), configurable per symbol. */
    ROUND
}
