package com.smarttrader.v2.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.orders.CancelOrdersRequest;
import com.coinbase.advanced.model.orders.CancelOrdersResponse;
import com.coinbase.advanced.model.orders.CancelResult;
import com.coinbase.advanced.model.orders.CreateOrderRequest;
import com.coinbase.advanced.model.orders.CreateOrderResponse;
import com.coinbase.advanced.model.orders.GetOrderRequest;
import com.coinbase.advanced.model.orders.GetOrderResponse;
import com.coinbase.advanced.model.orders.OrderConfiguration;
import com.coinbase.advanced.orders.OrdersService;
import com.smarttrader.v2.helper.OrderHelper;
import com.smarttrader.v2.model.Order;
import com.smarttrader.v2.model.OrderRequest;
import com.smarttrader.v2.model.OrderResponse;
import com.smarttrader.v2.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.smarttrader.v2.constants.OrderConstants.*;

/**
 * Service for placing, retrieving, and canceling orders via the CoinBase
 * Advanced Trade API. Every order is persisted in MongoDB for future
 * algorithmic-performance and P&amp;L analysis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

	private final ClientService clientService;
	private final OrderRepository orderRepository;
	private final UserService userService;
	private final Map<CoinbaseAdvancedClient, OrdersService> orderServiceCache = new ConcurrentHashMap<>();
	@Value("${coinbase.portfolio-id:}")
	private String portfolioId;

	/**
	 * Places a BUY or SELL order on CoinBase and persists the result.
	 *
	 * @param userId  the authenticated user
	 * @param request order parameters
	 * @return an {@link OrderResponse} describing the outcome
	 */
	//@CircuitBreaker(name = "orderService")
	public OrderResponse placeOrder(String userId, OrderRequest request) {
		try {
			validateOrderRequest(request);
		} catch (Exception e) {
			log.warn("Invalid order request for user [{}]: {}", userId, e.getMessage());
			return OrderHelper.buildOrderResponse(false, null, null,
					request.getProductId(), request.getSide(), request.getOrderType(),
					STATUS_FAILED, e.getMessage(), MSG_ORDER_FAILED);
		}

		OrdersService ordersService = OrderHelper.getOrderServiceFromCache(clientService, userId, orderServiceCache);
		if (ordersService == null) {
			log.error("No Coinbase client available for user [{}]. Cannot place order.", userId);
			return OrderHelper.buildOrderResponse(false, null, null,
					request.getProductId(), request.getSide(), request.getOrderType(),
					STATUS_FAILED, "no Coinbase credentials for user", MSG_ORDER_FAILED);
		}

		String clientOrderId = UUID.randomUUID().toString();
		OrderConfiguration orderConfig = OrderHelper.buildOrderConfiguration(request, orderRepository);

		CreateOrderRequest createRequest = new CreateOrderRequest.Builder()
				.productId(request.getProductId())
				.side(request.getSide())
				.clientOrderId(clientOrderId)
				.orderConfiguration(orderConfig)
				.retailPortfolioId(portfolioId)
				.build();

		Order order = new Order();
		order.setUserId(userId);
		order.setClientOrderId(clientOrderId);

		if (SIDE_SELL.equalsIgnoreCase(request.getSide())
				&& !validateAvailableQuantityForSell(request.getProductId(), request.getBaseSize() != null ? request.getBaseSize() : 0.0)) {
			log.info("Not enough quantity to sell for product: {}", request.getProductId());
			return OrderResponse.builder()
					.success(false)
					.message("Not enough quantity to sell for product: " + request.getProductId())
					.build();
		}

		try {
			log.info("Placing order for user [{}]: productId={}, side={}, orderType={}, REQUEST={}",
					userId, request.getProductId(), request.getSide(), request.getOrderType(),
					request);
			CreateOrderResponse response = ordersService.createOrder(createRequest);
			OrderResponse orderResponse = OrderHelper.prepareOrderToPersistFromExchangeResponse(request, response, order);
			if (orderResponse.isSuccess()) {
				synchronized (this) {
					orderRepository.save(order);
				}
				try {
					updateUserFundsAfterOrder(userId, order);
				} catch (Exception ex) {
					log.warn("Failed to update user funds after placing order: {}", ex.getMessage());
				}
				log.info("Order placed successfully for user [{}]: coinbaseOrderId={}", userId, response.getOrderId());
			}
			return orderResponse;
		} catch (CoinbaseAdvancedException e) {
			log.error("Coinbase API error placing order for user [{}]: {}", userId, e.getMessage());

			return OrderHelper.buildOrderResponse(false, order.getId(), null,
					request.getProductId(), request.getSide(), request.getOrderType(),
					STATUS_FAILED, e.getMessage(), MSG_COINBASE_ERROR);
		}
	}

	private void updateUserFundsAfterOrder(String userId, Order order) {
		double orderValue = order.getLimitPrice() * order.getQty();
		double currentFunds = userService.findByUserName(userId).getCurrentFunds();
		if (SIDE_BUY.equalsIgnoreCase(order.getSide())) {
			userService.updateUserFunds(userId, currentFunds - orderValue);
		} else if (SIDE_SELL.equalsIgnoreCase(order.getSide())) {
			userService.updateUserFunds(userId, currentFunds + orderValue);
		}
	}

	/**
	 * Retrieves a persisted order by its internal database ID.
	 */
	public Optional<Order> getOrder(String orderId) {
		return orderRepository.findById(orderId);
	}

	/**
	 * Retrieves all orders for a user, ordered by most recent first.
	 */
	public List<Order> getOrdersByUser(String userId) {
		return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
	}

	/**
	 * Retrieves all orders for a user and product, ordered by most recent first.
	 */
	public List<Order> getOrdersByUserAndProduct(String userId, String productId) {
		return orderRepository.findByUserIdAndProductIdOrderByCreatedAtDesc(userId, productId);
	}

	/**
	 * Cancels an order on CoinBase and updates the local record.
	 *
	 * @param userId  the authenticated user
	 * @param orderId the internal database order ID
	 * @return an {@link OrderResponse} describing the cancellation result
	 */
	public OrderResponse cancelOrder(String userId, String orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

		if (!order.getUserId().equals(userId)) {
			throw new IllegalArgumentException("Order does not belong to user: " + userId);
		}

		if (order.getCoinbaseOrderId() == null) {
			throw new IllegalArgumentException("Order has no Coinbase order ID — cannot cancel");
		}

		OrdersService ordersService = OrderHelper.getOrderServiceFromCache(clientService, userId, orderServiceCache);
		if (ordersService == null) {
			return OrderHelper.buildOrderResponse(false, orderId, order.getCoinbaseOrderId(),
					null, null, null, null, "no Coinbase credentials for user", MSG_CANCEL_FAILED);
		}

		try {
			CancelOrdersRequest cancelRequest = new CancelOrdersRequest.Builder()
					.orderIds(List.of(order.getCoinbaseOrderId()))
					.build();

			CancelOrdersResponse response = ordersService.cancelOrders(cancelRequest);

			if (response.getResults() != null && !response.getResults().isEmpty()) {
				CancelResult result = response.getResults().get(0);
				if (result.isSuccess()) {
					order.setStatus(STATUS_CANCELLED);
					order.setUpdatedAt(LocalDateTime.now());
					orderRepository.save(order);
					log.info("Order cancelled for user [{}]: orderId={}", userId, orderId);

					return OrderHelper.buildOrderResponse(true, orderId, order.getCoinbaseOrderId(),
							null, null, null,
							STATUS_CANCELLED, null, MSG_ORDER_CANCELLED);
				} else {
					log.warn("Cancel failed for order [{}]: {}", orderId, result.getFailureReason());
					return OrderHelper.buildOrderResponse(false, orderId, order.getCoinbaseOrderId(),
							null, null, null,
							null, result.getFailureReason(), MSG_CANCEL_FAILED);
				}
			}
			return OrderHelper.buildOrderResponse(false, orderId, null,
					null, null, null,
					null, null, MSG_CANCEL_NO_RESULT);

		} catch (CoinbaseAdvancedException e) {
			log.error("Coinbase API error cancelling order [{}]: {}", orderId, e.getMessage());
			return OrderHelper.buildOrderResponse(false, orderId, order.getCoinbaseOrderId(),
					null, null, null,
					null, e.getMessage(), MSG_CANCEL_API_ERROR);
		}
	}

	/**
	 * Fetches the latest order status from Coinbase and updates the local record.
	 *
	 * @param userId  the authenticated user
	 * @param orderId the internal database order ID
	 * @return the updated local {@link Order}
	 */
	public Order syncOrderStatus(String userId, String orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

		if (!order.getUserId().equals(userId)) {
			throw new IllegalArgumentException("Order does not belong to user: " + userId);
		}

		if (order.getCoinbaseOrderId() == null) {
			throw new IllegalArgumentException("Order has no Coinbase order ID — cannot sync");
		}

		OrdersService ordersService = OrderHelper.getOrderServiceFromCache(clientService, userId, orderServiceCache);
		if (ordersService == null) {
			throw new IllegalStateException("no Coinbase client available for user: " + userId);
		}

		try {
			GetOrderRequest getRequest = new GetOrderRequest.Builder()
					.orderId(order.getCoinbaseOrderId())
					.build();

			GetOrderResponse response = ordersService.getOrder(getRequest);
			com.coinbase.advanced.model.orders.Order cbOrder = response.getOrder();

			if (cbOrder != null) {
				order.setStatus(cbOrder.getStatus());
				order.setFilledSize(cbOrder.getFilledSize());
				order.setAverageFilledPrice(cbOrder.getAverageFilledPrice());
				order.setTotalFees(cbOrder.getTotalFees());
				order.setUpdatedAt(LocalDateTime.now());
				order.setComments("ENH-" + order.getComments());
				orderRepository.save(order);
				log.info("Order status synced for [{}]: status={}", orderId, cbOrder.getStatus());
			}

			return order;
		} catch (CoinbaseAdvancedException e) {
			log.error("Coinbase API error syncing order [{}]: {}", orderId, e.getMessage());
			throw new RuntimeException("Failed to sync order status: " + e.getMessage(), e);
		}
	}

	private void validateOrderRequest(OrderRequest request) {
		if (request.getProductId() == null || request.getProductId().isBlank()) {
			throw new IllegalArgumentException("productId is required");
		}
		if (request.getSide() == null || request.getSide().isBlank()) {
			throw new IllegalArgumentException("side is required");
		}
		String side = request.getSide().toUpperCase();
		if (!SIDE_BUY.equals(side) && !SIDE_SELL.equals(side)) {
			throw new IllegalArgumentException("side must be BUY or SELL");
		}
		if (request.getOrderType() == null || request.getOrderType().isBlank()) {
			throw new IllegalArgumentException("orderType is required");
		}
		String orderType = request.getOrderType().toUpperCase();
		if (!TYPE_MARKET.equals(orderType) && !TYPE_LIMIT.equals(orderType)) {
			throw new IllegalArgumentException("orderType must be MARKET or LIMIT");
		}
		if (TYPE_LIMIT.equals(orderType)) {
			if (request.getBaseSize() == null || request.getBaseSize() <= 0) {
				throw new IllegalArgumentException("baseSize is required and must be > 0 for LIMIT orders");
			}
			if (request.getLimitPrice() == null || request.getLimitPrice() <= 0) {
				throw new IllegalArgumentException("limitPrice is required and must be > 0 for LIMIT orders");
			}
		}
		if (TYPE_MARKET.equals(orderType)) {
			boolean hasBase = request.getBaseSize() != null && request.getBaseSize() > 0;
			boolean hasQuote = request.getQuoteSize() != null && request.getQuoteSize() > 0;
			if (!hasBase && !hasQuote) {
				throw new IllegalArgumentException("Either baseSize or quoteSize must be > 0 for MARKET orders");
			}
		}

		String productId = request.getProductId();
		Order order = prepareOrder(productId, request.getSide(), "SMART-TRADER-V2",
				request.getLimitPrice(), decideOrderQty(request.getLimitPrice()), "ADMIN");
		validateOrder(order);
	}

	/**
	 * Builds a synthetic Order for funds/quantity validation ahead of actual placement:
	 * adjusts qty down to what's available to sell, or down to what the user can afford to buy.
	 */
	private Order prepareOrder(String productId, String side, String comments, double price, double qty,
				String userName) {
		// Use cache to compute available quantities instead of direct DB call
		Map<String, Double> qtys = OrderHelper.getQtyBySideFromCache(orderRepository, productId);
		double buyQty = qtys.getOrDefault(SIDE_BUY, 0.0);
		double sellQty = qtys.getOrDefault(SIDE_SELL, 0.0);
		double qtyInHand = buyQty - sellQty;

		double originalQty = qty;

		if (userName == null || userName.isBlank()) {
			log.error("User name is null or blank, skipping funds validation for order preparation.");
			throw new IllegalArgumentException("User name is required for order preparation to validate funds.");
		}
		double requiredFunds = price * qty;
		double currentFunds = userService.findByUserName(userName).getCurrentFunds();
		double availableFunds = currentFunds - requiredFunds;
		if (qty > qtyInHand && side.equalsIgnoreCase(SIDE_SELL)) {
			qty = qtyInHand; // Adjust quantity to available quantity for sell
		}
		// We need 20 and have 10, so we can only buy 10 / price worth of quantity
		if (availableFunds < requiredFunds && side.equalsIgnoreCase(SIDE_BUY)) {
			qty = Math.floor(currentFunds / price); // Adjust quantity based on available funds
			log.warn("Adjusted order quantity from {} to {} for product: {} due to insufficient funds.",
					originalQty, qty, productId);
		}
		// Defensive validation: ensure qty is still positive after adjustments
		if (qty <= 0) {
			throw new IllegalArgumentException("Order quantity adjusted to zero or negative for product: " + productId);
		}

		Order order = new Order();
		order.setProductId(productId);
		order.setLimitPrice(price);
		order.setUserId(userName);
		order.setQty(qty);
		order.setSide(side);
		order.setComments(comments);
		order.setCreatedAt(LocalDateTime.now());
		return order;
	}

	private void validateOrder(Order order) {
		// Use cache to determine total available quantity instead of DB call
		Map<String, Double> qtys = OrderHelper.getQtyBySideFromCache(orderRepository, order.getProductId());
		Double availableFunds = userService.findByUserName(order.getUserId()).getCurrentFunds();
		double totalAvailableQty = qtys.getOrDefault(SIDE_BUY, 0.0) - qtys.getOrDefault(SIDE_SELL, 0.0);
		if (availableFunds != null && availableFunds <= 0) {
			throw new IllegalArgumentException(
					"User has insufficient funds to place BUY order. Available funds: " + availableFunds);
		}
		if (order.getQty() <= 0 || order.getLimitPrice() <= 0) {
			throw new IllegalArgumentException(
					"Order quantity must be greater than zero for orders. Given Qty: " + order.getQty());
		} else if (SIDE_SELL.equalsIgnoreCase(order.getSide())
				&& (totalAvailableQty <= 0 || order.getQty() > totalAvailableQty)) {
			throw new IllegalArgumentException("Not enough qty for product: " + order.getProductId()
					+ " to SELL. Available Qty: " + totalAvailableQty + ", Requested Qty: " + order.getQty());
		} else if (isDuplicateOrder(order.getProductId(), order.getSide(), order.getLimitPrice(), order.getQty())) {
			throw new IllegalArgumentException("Duplicate order detected for product: " + order.getProductId()
					+ ", side: " + order.getSide() + ", price: " + order.getLimitPrice() + ", qty: " + order.getQty()
					+ ". A similar order was placed within the last minute.");
		}
	}

	/** True if an order for the same product/side/price/qty was placed within the last minute. */
	private boolean isDuplicateOrder(String productId, String side, double price, double qty) {
		List<Order> orders = orderRepository.findByProductIdAndSide(productId, side);
		Order mostRecentMatch = orders.stream()
				.filter(o -> o.getLimitPrice() != null && o.getLimitPrice().equals(price) && o.getQty() == qty)
				.sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
				.findFirst()
				.orElse(null);
		if (mostRecentMatch == null || mostRecentMatch.getCreatedAt() == null) {
			return false;
		}
		LocalDateTime orderTimestamp = mostRecentMatch.getCreatedAt();
		LocalDateTime now = LocalDateTime.now();
		return orderTimestamp.getDayOfYear() == now.getDayOfYear()
				&& orderTimestamp.getYear() == now.getYear()
				&& orderTimestamp.getHour() == now.getHour()
				&& orderTimestamp.getMinute() == now.getMinute();
	}

	private double decideOrderQty(double price) {
		if (price <= 0) {
			log.warn("Price is zero or negative, cannot decide order quantity. Defaulting to qty=0.");
			return 0;
		}
		double qty = DEFAULT_ORDER_VALUE_IN_USD / price;
		return qty > 0 ? qty : 1;
	}

	private boolean validateAvailableQuantityForSell(String productId, double qty) {
		// Use cache to compute available quantity by side
		Map<String, Double> mapOfProductsBySide = OrderHelper.getQtyBySideFromCache(orderRepository, productId);

		Double buyQty = mapOfProductsBySide.getOrDefault(SIDE_BUY, 0.0);
		Double sellQty = mapOfProductsBySide.getOrDefault(SIDE_SELL, 0.0);
		Double avlblQty = buyQty - sellQty;
		log.info("Product: {}, Bought Qty: {}, Sold Qty: {}, Available Qty: {}", productId, buyQty, sellQty, avlblQty);
		return avlblQty >= qty;
	}

	/**
	 * Re-syncs the status of every locally-tracked order that isn't yet in a terminal
	 * state (FILLED or CANCELLED) against Coinbase.
	 */
	public void updateOrderStatusFromExchange() {
		List<Order> orders = orderRepository.findAll();
		try {
			orders.stream()
					.filter(o -> !"FILLED".equalsIgnoreCase(o.getStatus())
							&& !"CANCELLED".equalsIgnoreCase(o.getStatus()))
					.map(Order::getCoinbaseOrderId)
					.filter(id -> id != null && !id.isBlank())
					.parallel()
					.map(this::getOrderFromExchange)
					.filter(response -> response != null && response.getOrder() != null)
					.forEach(response -> {
						com.coinbase.advanced.model.orders.Order cbOrder = response.getOrder();
						Order order = orderRepository.findByCoinbaseOrderId(cbOrder.getOrderId()).orElse(null);
						if (order == null) {
							log.warn("No local order record found for Coinbase order ID: {}", cbOrder.getOrderId());
							return;
						}
						order.setStatus(cbOrder.getStatus());
						orderRepository.save(order);
						log.info("Updated order status from exchange for [{}]: status={}", order.getId(),
								cbOrder.getStatus());
					});
		} catch (Exception e) {
			log.error("Error updating order status from exchange: {}", e.getMessage());
		}
	}

	private GetOrderResponse getOrderFromExchange(String coinbaseOrderId) {
		// This method can be used to fetch the latest order details from CoinBase
		// and update the local order record. It can be called after placing an order
		// to ensure we have the correct status, filled size, etc.
		String userId = "ADMIN"; // Use a default user for fetching order status, or pass as parameter if needed
		OrdersService ordersService = OrderHelper.getOrderServiceFromCache(clientService, userId, orderServiceCache);
		if (ordersService == null) {
			log.error("OrdersService not found in cache for user {}. Cannot fetch order status from exchange.", userId);
			return null;
		}
		return ordersService.getOrder(new GetOrderRequest.Builder().orderId(coinbaseOrderId).build());
	}

	public List<Order> findAllOrders() {
		log.info("Fetching all orders from DB.");
		return orderRepository.findAll();
	}

	public List<Order> findByProductId(String productId) {
		return orderRepository.findByProductId(productId);
	}
}
