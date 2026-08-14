package com.smarttrader.v2.constants;

/**
 * Shared literals for the execution layer (OrderService/PositionService), per the
 * rebuilt Order/Position pipeline.
 */
public final class OrderConstants {

    public static final String SIDE_BUY = "BUY";
    public static final String SIDE_SELL = "SELL";
    public static final String ORDER_TYPE_MARKET = "MARKET";
    public static final String ORDER_TYPE_LIMIT = "LIMIT";
    
 // -- Order statuses --
 	public static final String STATUS_PENDING = "PENDING";
 	public static final String STATUS_PLACED = "PLACED";
 	public static final String STATUS_FAILED = "FAILED";
 	public static final String STATUS_CANCELLED = "CANCELLED";

 	// -- Response messages --
 	public static final String MSG_ORDER_PLACED = "Order placed successfully";
 	public static final String MSG_ORDER_FAILED = "Order placement failed";
 	public static final String MSG_COINBASE_ERROR = "Coinbase API error";
 	public static final String MSG_ORDER_CANCELLED = "Order cancelled successfully";
 	public static final String MSG_CANCEL_FAILED = "Order cancellation failed";
 	public static final String MSG_CANCEL_NO_RESULT = "No cancellation result returned from Coinbase";
 	public static final String MSG_CANCEL_API_ERROR = "Coinbase API error during cancellation";
 	
 	/** Every live order must be sized to exactly this many USD notional - see OrderService. */
 	public static final double FIXED_ORDER_VALUE_USD = 11.00;

    private OrderConstants() {
    }
}
