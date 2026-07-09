package com.smarttrader.v2.constants;

/**
 * Constants used across order-related classes (service, controller, validation).
 *
 * <p>Centralises magic strings to avoid typos and simplify future changes.</p>
 */
public final class OrderConstants {

	private OrderConstants() {
		// utility class — not instantiable
	}

	// -- Order sides --
	public static final String SIDE_BUY = "BUY";
	public static final String SIDE_SELL = "SELL";

	// -- Order types --
	public static final String TYPE_MARKET = "MARKET";
	public static final String TYPE_LIMIT = "LIMIT";

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
	
	public static final double MAX_USD_PER_ORDER = 50.00;

	/** Default USD notional used to size a new order when no explicit quantity is given. */
	public static final double DEFAULT_ORDER_VALUE_IN_USD = 50.00;
}
