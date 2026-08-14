package com.smarttrader.v2.model;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;

/**
 * Inbound DTO for placing a new order.
 *
 * <p>The caller specifies the trading pair, side (BUY/SELL), order type
 * (MARKET/LIMIT), quantity, and — for limit orders — the limit price.
 * Optional {@code quoteSize} can be used for market orders sized in the
 * quote currency.</p>
 */
@Data
@Builder
public class OrderRequest {

	/** Trading pair, e.g. "BTC-USDC". Required. */
	@NotBlank(message = "productId is required")
	private String productId;

	/** BUY or SELL. Required. */
	@NotBlank(message = "side is required")
	@Pattern(regexp = "(?i)BUY|SELL", message = "side must be BUY or SELL")
	private String side;

	/** MARKET or LIMIT. Required. */
	@NotBlank(message = "orderType is required")
	@Pattern(regexp = "(?i)MARKET|LIMIT", message = "orderType must be MARKET or LIMIT")
	private String orderType;

	/** Base-currency quantity. Required for LIMIT orders and base-sized MARKET orders. */
	@Positive(message = "baseSize must be greater than 0")
	private Double baseSize;

	/** Limit price. Required for LIMIT orders. */
	@Positive(message = "limitPrice must be greater than 0")
	private Double limitPrice;

	/** Quote-currency size. Optional; used for quote-sized MARKET orders. */
	@Positive(message = "quoteSize must be greater than 0")
	private Double quoteSize;

	/** Optional decision factors or strategy metadata. */
	private Map<String, String> decisionFactors;

	/** Optional free-form comment. */
	private String comments;

	/**
	 * Stop-loss price computed by the risk manager for this order.
	 * Persisted to {@link com.techcobber.smarttrader.v1.models.Order} so that
	 * {@link com.techcobber.smarttrader.v1.services.TradingOrchestrator#evaluateExit}
	 * can recompute the effective stop floor without needing the original candle data.
	 */
	private Double stopLoss;

	/**
	 * Take-profit price computed by the risk manager.
	 * Persisted for P&amp;L tracking and exit-target monitoring.
	 */
	private Double takeProfit;

	/**
	 * Intended entry price at the time the order was raised (usually the current
	 * market price / limit price). Persisted as a numeric convenience field so
	 * exit math in {@code evaluateExit()} does not have to parse
	 * {@code averageFilledPrice} which may be null until the order is filled.
	 */
	private Double entryPriceNum;
}
