package com.smarttrader.v2.model;

import lombok.Builder;
import lombok.Data;

/**
 * Outcome of an OrderService.placeOrder()/cancelOrder() call.
 */
@Data
@Builder
public class OrderResponse {

	private boolean success;

	private String orderId;

	private String coinbaseOrderId;

	private String productId;

	private String side;

	private String orderType;

	private String status;

	private String failureReason;

	private String message;
}
