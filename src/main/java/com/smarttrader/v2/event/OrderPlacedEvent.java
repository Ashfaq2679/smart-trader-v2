package com.smarttrader.v2.event;

/**
 * Fired when OrderService successfully places a real order on Coinbase (live mode only -
 * dry-run orders don't fire this, see OrderService's javadoc).
 */
public class OrderPlacedEvent extends TradingEvent {

    public String orderId;
    public String coinbaseOrderId;
    public String side;
    public double baseSize;

    public OrderPlacedEvent() {
        super("execution.OrderPlaced");
    }
}
