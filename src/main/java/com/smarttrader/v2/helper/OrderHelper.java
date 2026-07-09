package com.smarttrader.v2.helper;

import static com.smarttrader.v2.constants.OrderConstants.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.factory.CoinbaseAdvancedServiceFactory;
import com.coinbase.advanced.model.orders.CreateOrderResponse;
import com.coinbase.advanced.model.orders.LimitGtc;
import com.coinbase.advanced.model.orders.MarketIoc;
import com.coinbase.advanced.model.orders.OrderConfiguration;
import com.coinbase.advanced.model.orders.TriggerGtc;
import com.coinbase.advanced.orders.OrdersService;
import com.smarttrader.v2.model.Order;
import com.smarttrader.v2.model.OrderRequest;
import com.smarttrader.v2.model.OrderResponse;
import com.smarttrader.v2.repository.OrderRepository;
import com.smarttrader.v2.service.ClientService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderHelper {

	private OrderHelper() {
		// utility class — not instantiable
	}

	/**
	 * Prepares an OrderResponse object based on the provided OrderRequest, CreateOrderResponse, and Order entities. It populates the Order entity with relevant details from the request and response, and sets the appropriate status and messages based on the success or failure of the order placement.
	 *
	 * @param request The OrderRequest containing the details of the order to be placed.
	 * @param orderResponse The CreateOrderResponse received from the CoinBase API after attempting to place the order.
	 * @param order The Order entity that will be populated with details and persisted to the database.
	 * @return An OrderResponse object containing the outcome of the order placement, including success status, order IDs, product details, and any failure reasons or messages.
	 */
	public static OrderResponse prepareOrderToPersistFromExchangeResponse(OrderRequest request, CreateOrderResponse orderResponse, Order order) {
		order.setProductId(request.getProductId());
		order.setSide(request.getSide().toUpperCase());
		order.setOrderType(request.getOrderType().toUpperCase());
		order.setQty(request.getBaseSize() != null ? request.getBaseSize() : 0.0);
		order.setLimitPrice(request.getLimitPrice());
		order.setQuoteSize(request.getQuoteSize());
		order.setDecisionFactors(request.getDecisionFactors());
		order.setComments(request.getComments());
		// Persist risk management fields so exit evaluation can use them later
		order.setStopLoss(request.getStopLoss());
		order.setTakeProfit(request.getTakeProfit());
		order.setEntryPriceNum(request.getEntryPriceNum());
		order.setCreatedAt(LocalDateTime.now());
		order.setUpdatedAt(LocalDateTime.now());

		if (orderResponse.isSuccess()) {
			order.setCoinbaseOrderId(orderResponse.getSuccessResponse()
					.getOrderId());
			order.setStatus(STATUS_PLACED);
			if (orderResponse.getOrderConfiguration() != null && orderResponse.getOrderConfiguration().getLimitLimitGtc() != null) {
				order.setQty(Double.parseDouble(orderResponse.getOrderConfiguration()
						.getLimitLimitGtc()
						.getBaseSize()));
				order.setLimitPrice(
						Double.parseDouble(orderResponse.getOrderConfiguration()
								.getLimitLimitGtc()
								.getLimitPrice()));
			}
			return buildOrderResponse(true, order.getId(), orderResponse.getOrderId(),
					request.getProductId(), request.getSide(), request.getOrderType(),
					STATUS_PENDING, null, MSG_ORDER_PLACED);
		} else {
			order.setStatus(STATUS_FAILED);
			order.setComments(orderResponse.getFailureReason());
			return buildOrderResponse(false, order.getId(), null,
					request.getProductId(), request.getSide(), request.getOrderType(),
					STATUS_FAILED, orderResponse.getFailureReason(), MSG_ORDER_FAILED);
		}

	}

	public static OrderResponse buildOrderResponse(boolean success, String orderId,
			String coinbaseOrderId, String productId, String side,
			String orderType, String status, String failureReason, String message) {

		return OrderResponse.builder()
				.success(success)
				.orderId(orderId)
				.coinbaseOrderId(coinbaseOrderId)
				.productId(productId)
				.side(side)
				.orderType(orderType)
				.status(status)
				.failureReason(failureReason)
				.message(message)
				.build();
	}

	/**
	 * Retrieves the OrdersService instance from the cache based on the user ID. If the instance is not present in the cache, it creates a new one and stores it in the cache.
	 *
	 * @param clientService The ClientService instance to retrieve the CoinbaseAdvancedClient for the user.
	 * @param userId The ID of the user for whom to retrieve the OrdersService.
	 * @param orderServiceCache The cache map that holds the OrdersService instances keyed by CoinbaseAdvancedClient.
	 * @return The OrdersService instance associated with the user's CoinbaseAdvancedClient.
	 */
	public static OrdersService getOrderServiceFromCache(ClientService clientService, String userId, Map<CoinbaseAdvancedClient, OrdersService> orderServiceCache) {
		CoinbaseAdvancedClient client = clientService.getCoinbaseClientForUserFromCache(userId);
		if (client == null) {
			return null;
		}
		// computeIfAbsent is atomic: orderServiceCache must be a ConcurrentHashMap
		// (this is called from parallel streams in OrderService.updateOrderStatusFromExchange).
		return orderServiceCache.computeIfAbsent(client, CoinbaseAdvancedServiceFactory::createOrdersService);
	}

	public static OrderConfiguration buildOrderConfiguration(OrderRequest request, OrderRepository orderRepository) {
		String orderType = request.getOrderType().toUpperCase();
		Double baseSize = request.getBaseSize();

		if (TYPE_MARKET.equals(orderType)) {
			MarketIoc.Builder marketBuilder = new MarketIoc.Builder();
			if (request.getBaseSize() != null && request.getBaseSize() > 0) {
				marketBuilder.baseSize(String.valueOf(request.getBaseSize()));
			} else {
				marketBuilder.quoteSize(String.valueOf(request.getQuoteSize()));
			}
			return new OrderConfiguration.Builder()
					.marketMarketIoc(marketBuilder.build())
					.build();
		} else if (request.getStopLoss() != null && request.getTakeProfit() != null) {
			return new OrderConfiguration.Builder()
					.triggerBracketGtc(new TriggerGtc.Builder()
							.baseSize(String.valueOf(baseSize))
							.limitPrice(String.format("%.2f", request.getTakeProfit()))
							.stopTriggerPrice(String.format("%.2f", request.getStopLoss()))
							.build())
					.build();
		} else {
			Double limitPrice = request.getLimitPrice();
			// find 0.5% of limit price and subtract from limit price to set as stop price,
			// this is to make sure the post only orders won't fail.
			if (request.getSide().equalsIgnoreCase(SIDE_SELL)) {
				limitPrice = request.getLimitPrice() + (request.getLimitPrice() * 0.005);
				Double availableQty = findAvailableQtyForProduct(orderRepository, request.getProductId());
				if (availableQty != null && availableQty > 0 && request.getBaseSize() > availableQty) {
					log.info("Adjusting sell order quantity from {} to {} for product: {} based on available quantity.",
							request.getBaseSize(), availableQty, request.getProductId());
					 baseSize = availableQty;
				}
			} else {
				limitPrice = request.getLimitPrice() - (request.getLimitPrice() * 0.001);
				// Cap qty (baseSize) so the order's notional value (qty * price) never exceeds
				// MAX_USD_PER_ORDER.
				double requestedBaseSize = request.getBaseSize() != null ? request.getBaseSize() : 1.0;
				double notionalValue = requestedBaseSize * request.getLimitPrice();

				if (notionalValue > MAX_USD_PER_ORDER) {
					double adjustedBaseSize = Math.floor(MAX_USD_PER_ORDER / request.getLimitPrice() * 1e8) / 1e8; // avoid floating precision
					log.info("Adjusting buy order quantity from {} to {} for product: {} to enforce max order value of ${}.",
							requestedBaseSize, adjustedBaseSize, request.getProductId(), MAX_USD_PER_ORDER);
					baseSize = adjustedBaseSize;
				} else {
					baseSize = requestedBaseSize;
				}
			}
			LimitGtc limitGtc = new LimitGtc.Builder()
					.baseSize(String.valueOf(baseSize))
					.limitPrice(String.format("%.2f", limitPrice))	//Must be a string with 2 decimal places to avoid CoinBase API validation error.
					.postOnly(true)
					.build();
			return new OrderConfiguration.Builder()
					.limitLimitGtc(limitGtc)
					.build();
		}
	}

	private static Double findAvailableQtyForProduct(OrderRepository orderRepository, String productId) {
		Map<String, Double> result = getQtyBySideFromCache(orderRepository, productId);
		double buyQty = result.get(SIDE_BUY);
		double sellQty = result.get(SIDE_SELL);
		return buyQty - sellQty;
	}

	public static Map<String, Double> getQtyBySideFromCache(OrderRepository orderRepository, String productId) {
		Map<String, Double> result = new HashMap<>();
		List<Order> orders = orderRepository.findByProductId(productId);
		double buy = orders.stream().filter(o -> SIDE_BUY.equalsIgnoreCase(o.getSide())).mapToDouble(Order::getQty)
				.sum();
		double sell = orders.stream().filter(o -> SIDE_SELL.equalsIgnoreCase(o.getSide())).mapToDouble(Order::getQty)
				.sum();
		result.put(SIDE_BUY, buy);
		result.put(SIDE_SELL, sell);
		return result;
	}
}
