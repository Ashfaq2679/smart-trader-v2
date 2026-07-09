package com.smarttrader.v2.model;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Input to OrderService.placeOrder(): the order parameters requested by a caller
 * (e.g. an execution-layer integration acting on an approved TradeDecision).
 */
@Data
@Builder
public class OrderRequest {

	private String productId;

	private String side;

	private String orderType;

	private Double baseSize;

	private Double quoteSize;

	private Double limitPrice;

	private Double stopLoss;

	private Double takeProfit;

	private Double entryPriceNum;

	private Map<String, Object> decisionFactors;

	private String comments;
}
