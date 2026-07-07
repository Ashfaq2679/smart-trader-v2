package com.smarttrader.v2.execution;

/**
 * Outcome of an order execution attempt, per V2_TECH_SPEC_v1.1.md section 6
 * (Order Execution Realism).
 */
public enum OrderStatus {
    /** Passed all realism checks and is ready to submit to the exchange. */
    PLACED,
    /** Not attempted: the upstream TradeDecision was not approved. */
    REJECTED,
    /** Quoted price moved beyond the slippage tolerance. */
    CANCELLED,
    /** The signal's validity window elapsed before the order could be placed. */
    EXPIRED
}
