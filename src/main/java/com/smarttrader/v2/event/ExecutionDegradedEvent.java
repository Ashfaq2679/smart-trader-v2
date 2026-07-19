package com.smarttrader.v2.event;

/**
 * Fired whenever the system fails to do what it's supposed to do - take an
 * approved decision from analysis and place a real market order - despite live
 * execution being enabled. This is deliberately distinct from the normal, expected
 * "dry-run" state (smart-trader.execution.live-enabled=false): dry-run is a quiet,
 * documented default, not a degradation. This event is for the cases that matter -
 * live trading was supposed to happen and didn't:
 *
 *   - smart-trader.execution.live-enabled=true but coinbase.api.key-name/private-key
 *     aren't configured (order credentials missing)
 *   - a live order submission to Coinbase fails (API error, exception, rejected order)
 *
 * NotificationFacadeService renders this as a BOLD banner in the logs (see its javadoc)
 * so a missing credential or a failed live order is never a quiet log line.
 */
public class ExecutionDegradedEvent extends TradingEvent {

    public String reason;
    public String detail;

    public ExecutionDegradedEvent() {
        super("execution.Degraded");
    }
}
