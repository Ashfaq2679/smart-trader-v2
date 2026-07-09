package com.smarttrader.v2.model;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document recording an order submitted to Coinbase, per CLAUDE.md's "orders"
 * collection. Persisted by OrderService for every placement attempt (successful or not)
 * so order history, duplicate detection, and available-quantity calculations can be
 * derived from the database rather than re-queried from the exchange.
 */
@Data
@Document("orders")
public class Order {

	@Id
	private String id;

	@Indexed
	private String userId;

	@Indexed(unique = true, sparse = true)
	private String clientOrderId;

	@Indexed(unique = true, sparse = true)
	private String coinbaseOrderId;

	@Indexed
	private String productId;

	private String side;

	private String orderType;

	private double qty;

	private Double limitPrice;

	private Double quoteSize;

	private Map<String, Object> decisionFactors;

	private String comments;

	private Double stopLoss;

	private Double takeProfit;

	private Double entryPriceNum;

	private String status;

	/** Raw string fields, matching the Coinbase SDK's Order model representation. */
	private String filledSize;
	private String averageFilledPrice;
	private String totalFees;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;
}
