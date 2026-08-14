package com.smarttrader.v2.event;

/**
 * Fired when OrderService attempts a live order placement and it fails (Coinbase error,
 * exception, or missing/invalid credentials). Always paired with an ExecutionDegradedEvent
 * (see its javadoc) since a failed live order is exactly the system "moving away" from
 * placing market orders.
 */
public class OrderFailedEvent extends TradingEvent {

    public String orderId;
    public String side;
    public double baseSize;
    public String failureReason;

    public OrderFailedEvent() {
        super("execution.OrderFailed");
    }
}
