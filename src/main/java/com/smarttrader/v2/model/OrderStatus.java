package com.smarttrader.v2.model;

/**
 * Lifecycle status of an Order, per execution-layer rebuild (see OrderService javadoc).
 */
public enum OrderStatus {
    /** Logged/persisted only; smart-trader.execution.live-enabled was false. */
    DRY_RUN,
    /** Submitted to Coinbase successfully. */
    PLACED,
    /** Coinbase confirmed a fill. */
    FILLED,
    /** Submission failed (Coinbase error, missing credentials, exception). */
    FAILED
}
