package com.smarttrader.v2.execution;

import static com.smarttrader.v2.constants.OrderConstants.ORDER_TYPE_MARKET;
import static com.smarttrader.v2.constants.OrderConstants.SIDE_BUY;
import static com.smarttrader.v2.constants.OrderConstants.SIDE_SELL;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.factory.CoinbaseAdvancedServiceFactory;
import com.coinbase.advanced.model.orders.CreateOrderRequest;
import com.coinbase.advanced.model.orders.CreateOrderResponse;
import com.coinbase.advanced.model.orders.LimitGtc;
import com.coinbase.advanced.model.orders.MarketIoc;
import com.coinbase.advanced.model.orders.OrderConfiguration;
import com.coinbase.advanced.model.orders.TriggerGtc;
import com.coinbase.advanced.orders.OrdersService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrader.v2.client.ClientService;
import com.smarttrader.v2.client.CoinbaseProperties;
import com.smarttrader.v2.constants.OrderConstants;
import com.smarttrader.v2.event.ExecutionDegradedEvent;
import com.smarttrader.v2.event.OrderFailedEvent;
import com.smarttrader.v2.event.OrderPlacedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Order;
import com.smarttrader.v2.model.OrderRequest;
import com.smarttrader.v2.model.OrderStatus;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns an approved TradeDecision into a market order - the "put market orders based on
 * the decision from analysis" half of the pipeline (TradingScheduler drives the "analysis"
 * half: AnalysisContextBuilder -> TradeEngine -> TradeDecision).
 *
 * Dry-run by default (smart-trader.execution.live-enabled=false): every approved decision
 * is logged and persisted as an Order with status=DRY_RUN, nothing reaches Coinbase. This
 * is the expected, quiet default state - not a degradation, no alert.
 *
 * Once live-enabled=true, a real MARKET order is placed via smart-trader-v1's tested
 * client-creation path: ClientService.getClientForUser(userId) resolves (building and
 * caching on demand) a CoinbaseAdvancedClient from that user's encrypted, DB-stored
 * credentials (CoinbaseClientFactory.buildClient(): decrypt -> CoinbaseAdvancedCredentials
 * -> CoinbaseAdvancedClient - the official SDK's client is the only thing that can
 * actually talk to Coinbase). An OrdersService is then resolved from that client (and
 * cached per-client, per v1's OrderHelper.getOrderServiceFromCache) before submitting the
 * order. If live mode is on but can't actually place a real order for any reason -
 * missing/invalid credentials for that user, a Coinbase rejection, an exception - that IS
 * the system moving away from its stated target, so it publishes ExecutionDegradedEvent,
 * which NotificationFacadeService renders as a BOLD banner. Silence is never the failure
 * mode here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** Identity used for scheduler-driven (TradeDecision) order placement - see execute(). */
    private static final String USER_ID = "ADMIN";

    private final ClientService clientService;
    private final CoinbaseProperties coinbaseProperties;
    private final OrderRepository orderRepository;
    private final TradingEventPublisher eventPublisher;

    /** Per-client OrdersService cache, per v1's OrderHelper.getOrderServiceFromCache. */
    private final Map<CoinbaseAdvancedClient, OrdersService> orderServiceCache = new ConcurrentHashMap<>();

    @Value("${smart-trader.execution.live-enabled:false}")
    private boolean liveEnabled;

    public Optional<Order> execute(TradeDecision decision, String symbol, AnalysisContext ctx) {
        if (!decision.approved()) {
            return Optional.empty();
        }

        String side = toSide(decision.signal().direction());
        if (side == null) {
            log.warn("orderService symbol={} approved decision has direction=NONE, nothing to place", symbol);
            return Optional.empty();
        }

        double entryPrice = decision.signal().entry();
        if (entryPrice <= 0) {
            log.warn("orderService symbol={} approved decision has entry price {} <= 0, cannot size a fixed-$"
                    + "{} order, nothing to place", symbol, entryPrice, OrderConstants.FIXED_ORDER_VALUE_USD);
            return Optional.empty();
        }
        // Every order is sized to exactly FIXED_ORDER_VALUE_USD notional, independent of
        // RiskEngine's positionSize() - see OrderConstants.FIXED_ORDER_VALUE_USD.
        double fixedBaseSize = OrderConstants.FIXED_ORDER_VALUE_USD / entryPrice;

        OrderRequest orderRequest = OrderRequest.builder()
				.productId(symbol)
				.side(side)
				.orderType("LIMIT")
				.baseSize(fixedBaseSize)
				.limitPrice(decision.signal().entry())
				//.stopLoss(decision.signal().stop())
				//.takeProfit(decision.signal().target())
				// Current market price, not the signal's entry: used by
				// buildOrderConfiguration() to re-derive baseSize for MARKET SELL orders
				// so notional stays as close to FIXED_ORDER_VALUE_USD as the live price allows.
				.entryPriceNum(ctx.price())
				.build();

        Order order = Order.builder()
                .symbol(symbol)
                .side(side)
                .orderType(OrderConstants.ORDER_TYPE_MARKET)
                .baseSize(fixedBaseSize)
                .entryPrice(decision.signal().entry())
                .stopPrice(decision.signal().stop())
                .targetPrice(decision.signal().target())
                .clientOrderId(UUID.randomUUID().toString())
                .strategyName(decision.signal().strategyName())
                .regime(decision.regime())
                .createdAtNs(System.nanoTime())
                .createdAt(LocalDateTime.now(ZoneId.of("America/New_York")))
                .analysisContext(ctx)
                .build();

        if (!liveEnabled) {
            order.setDryRun(true);
            order.setStatus(OrderStatus.DRY_RUN);
            orderRepository.save(order);
            log.info("orderService DRY-RUN symbol={} side={} baseSize={} strategy={} regime={} (live-enabled=false)",
                    symbol, side, order.getBaseSize(), order.getStrategyName(), order.getRegime());
            return Optional.of(order);
        }

        order.setDryRun(false);
        return Optional.of(placeLive(orderRequest, decision, ctx, USER_ID));
    }

    /**
     * Places a market order directly from a pre-built Order - a manual/API-triggered
     * placement path, as opposed to execute()'s TradeDecision-driven path. Reuses the
     * same dry-run/live logic as execute() (see placeLive()'s javadoc for what "live"
     * means) rather than duplicating it.
     *
     * Ported from smart-trader-v1's OrderService.placeOrder, including its per-user
     * client resolution (see placeLive()). Kept from v1: rejecting an exact duplicate of
     * a very recent order (see isDuplicateOrder()). Not ported: v1's per-user funds/
     * quantity ledger validation (UserService) - v2 has no funds/position ledger to
     * validate against.
     *
     * @param userId caller identity; selects which user's Coinbase credentials to use
     * @param orderRequest a populated Order (symbol/side/baseSize required)
     */
//    public Optional<Order> placeOrder(String userId, Order orderRequest, TradeDecision decision, AnalysisContext ctx) {
//        String effectiveUserId = (userId == null || userId.isBlank()) ? USER_ID : userId;
//
//        if (orderRequest.getSymbol() == null || orderRequest.getSymbol().isBlank()
//                || orderRequest.getSide() == null || orderRequest.getSide().isBlank()
//                || orderRequest.getBaseSize() <= 0) {
//            log.warn("orderService placeOrder userId={} rejected: symbol/side required and baseSize must be > 0",
//                    effectiveUserId);
//            return Optional.empty();
//        }
//
//        if (orderRequest.getClientOrderId() == null || orderRequest.getClientOrderId().isBlank()) {
//            orderRequest.setClientOrderId(UUID.randomUUID().toString());
//        }
//        if (orderRequest.getOrderType() == null || orderRequest.getOrderType().isBlank()) {
//            orderRequest.setOrderType(OrderConstants.ORDER_TYPE_MARKET);
//        }
//        if (orderRequest.getCreatedAtNs() == 0) {
//            orderRequest.setCreatedAtNs(System.nanoTime());
//        }
//        if (orderRequest.getCreatedAt() == null) {
//            orderRequest.setCreatedAt(LocalDateTime.now(ZoneId.of("America/New_York")));
//        }
//
//        if (isDuplicateOrder(orderRequest)) {
//            log.warn("orderService placeOrder userId={} symbol={} side={} baseSize={} rejected: duplicate of a recent order",
//                    effectiveUserId, orderRequest.getSymbol(), orderRequest.getSide(), orderRequest.getBaseSize());
//            return Optional.empty();
//        }
//
//        log.info("orderService placeOrder userId={} symbol={} side={} baseSize={} (live-enabled={})",
//                effectiveUserId, orderRequest.getSymbol(), orderRequest.getSide(), orderRequest.getBaseSize(), liveEnabled);
//
//        if (!liveEnabled) {
//            orderRequest.setDryRun(true);
//            orderRequest.setStatus(OrderStatus.DRY_RUN);
//            orderRepository.save(orderRequest);
//            log.info("orderService DRY-RUN symbol={} side={} baseSize={} (live-enabled=false)",
//                    orderRequest.getSymbol(), orderRequest.getSide(), orderRequest.getBaseSize());
//            return Optional.of(orderRequest);
//        }
//
//        orderRequest.setDryRun(false);
//        return Optional.of(placeLive(orderRequest, decision, ctx, effectiveUserId));
//    }

    /**
     * True if an order for the same symbol/side/baseSize was already placed within the
     * current minute, per v1 OrderHelper.isDuplicateOrder (ported: same guard, adapted to
     * v2's Order/OrderRepository - v1 compared against its own per-product order history
     * the same way).
     */
    private boolean isDuplicateOrder(Order orderRequest) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/New_York"));
        return orderRepository.findBySymbolOrderByCreatedAtDesc(orderRequest.getSymbol()).stream()
                .filter(o -> orderRequest.getSide().equalsIgnoreCase(o.getSide())
                        && o.getBaseSize() == orderRequest.getBaseSize())
                .map(Order::getCreatedAt)
                .filter(Objects::nonNull)
                .anyMatch(createdAt -> createdAt.getYear() == now.getYear()
                        && createdAt.getDayOfYear() == now.getDayOfYear()
                        && createdAt.getHour() == now.getHour()
                        && createdAt.getMinute() == now.getMinute());
    }

    private Order placeLive(OrderRequest orderRequest, TradeDecision decision, AnalysisContext ctx, String userId) {
        OrdersService ordersService;
        
        Order order = Order.builder()
                .symbol(orderRequest.getProductId())
                .side(orderRequest.getSide())
                .orderType(OrderConstants.ORDER_TYPE_MARKET)
                .baseSize(orderRequest.getBaseSize())
                .entryPrice(decision.signal().entry())
                .stopPrice(decision.signal().stop())
                .targetPrice(decision.signal().target())
                .clientOrderId(UUID.randomUUID().toString())
                .strategyName(decision.signal().strategyName())
                .regime(decision.regime())
                .createdAtNs(System.nanoTime())
                .createdAt(LocalDateTime.now(ZoneId.of("America/New_York")))
                .analysisContext(ctx)
                .build();
        try {
            ordersService = resolveOrdersService(userId);
        } catch (Exception e) {
            return fail(order, "missing order credentials",
                    "no Coinbase client available for user " + userId + ": " + e.getMessage());
        }

        try {
            CreateOrderRequest request = new CreateOrderRequest.Builder()
                    .productId(order.getSymbol())
                    .side(order.getSide())
                    .clientOrderId(order.getClientOrderId())
                    .orderConfiguration(buildOrderConfiguration(orderRequest))
                    .retailPortfolioId(coinbaseProperties.portfolioId())
                    .build();
            
            log.info("Submitting LIVE order {}", new ObjectMapper().writeValueAsString(request));
            CreateOrderResponse response = ordersService.createOrder(request);

            if (response.isSuccess()) {
                order.setStatus(OrderStatus.PLACED);
                order.setCoinbaseOrderId(response.getOrderId());
                orderRepository.save(order);

                OrderPlacedEvent event = new OrderPlacedEvent();
                event.symbol = order.getSymbol();
                event.orderId = order.getId();
                event.coinbaseOrderId = order.getCoinbaseOrderId();
                event.side = order.getSide();
                event.baseSize = order.getBaseSize();
                eventPublisher.publish(event);

                log.info("orderService LIVE order placed userId={} symbol={} side={} baseSize={} coinbaseOrderId={}",
                        userId, order.getSymbol(), order.getSide(), order.getBaseSize(), order.getCoinbaseOrderId());
                return order;
            }

            return fail(order, "live order rejected by Coinbase", response.getErrorResponse() != null ? response.getErrorResponse().getMessage() : "unknown error");
        } catch (CoinbaseAdvancedException e) {
            return fail(order, "live order submission failed (Coinbase API error)", e.getMessage());
        } catch (Exception e) {
            return fail(order, "live order submission threw an unexpected exception", e.getMessage());
        }
    } 
    
    
    private OrderConfiguration buildOrderConfiguration(OrderRequest request) {

		String orderType = request.getOrderType().toUpperCase();
		Double baseSize = request.getBaseSize();

		if (ORDER_TYPE_MARKET.equals(orderType)) {
			MarketIoc.Builder marketBuilder = new MarketIoc.Builder();
			if (SIDE_BUY.equalsIgnoreCase(request.getSide())) {
				// quoteSize spends an exact dollar amount - the only way to guarantee $11
				// notional precisely for a market BUY (baseSize would drift with fill price).
				marketBuilder.quoteSize(toPlainString(OrderConstants.FIXED_ORDER_VALUE_USD, 2));
			} else {
				// Market SELL has no quoteSize equivalent (you sell units, not a dollar
				// amount), so baseSize must be recomputed here against the current price
				// (entryPriceNum, set from ctx.price() in execute()) rather than trusting
				// request.getBaseSize() as-is - that value was sized off the signal's own
				// entry price, which can drift from the price actually in effect when the
				// order is submitted moments later, pulling notional away from $11.
				double currentPrice = request.getEntryPriceNum() != null && request.getEntryPriceNum() > 0
						? request.getEntryPriceNum()
						: request.getLimitPrice();
				double adjustedBaseSize = OrderConstants.FIXED_ORDER_VALUE_USD / currentPrice;
				marketBuilder.baseSize(toPlainString(adjustedBaseSize, 3));
			}
			return new OrderConfiguration.Builder()
					.marketMarketIoc(marketBuilder.build())
					.build();
		} else if (request.getStopLoss() != null && request.getTakeProfit() != null) {
			return new OrderConfiguration.Builder()
					.triggerBracketGtc(new TriggerGtc.Builder()
							.baseSize(toPlainString(baseSize, 3))
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
				// request.getBaseSize() was already sized to FIXED_ORDER_VALUE_USD / entry price
				// (see execute()); use it as-is so every order's notional stays exactly $11 -
				// no recomputation here, since that previously replaced a correct quantity with
				// a nonsense one (FIXED_ORDER_VALUE_USD / baseSize instead of / price).
				double currentPrice = request.getEntryPriceNum() != null && request.getEntryPriceNum() > 0
						? request.getEntryPriceNum()
						: request.getLimitPrice();
				baseSize = OrderConstants.FIXED_ORDER_VALUE_USD / currentPrice;
			
			}
			LimitGtc limitGtc = new LimitGtc.Builder()
					.baseSize(toPlainString(baseSize, 3))
					.limitPrice(String.format("%.2f", limitPrice))	//Must be a string with 2 decimal places to avoid CoinBase API validation error.
					.postOnly(true)
					.build(); 
			return new OrderConfiguration.Builder()
					.limitLimitGtc(limitGtc)
					.build(); 
		}
    }

    /**
     * Resolves the OrdersService for a user, per smart-trader-v1's
     * OrderHelper.getOrderServiceFromCache - ported here since v2 has no separate
     * OrderHelper utility class. ClientService.getClientForUser(userId) builds (on a
     * cache miss) or reuses a cached CoinbaseAdvancedClient from that user's encrypted,
     * DB-stored credentials; the OrdersService wrapping that client is then cached here
     * per-client so repeated calls for the same client reuse it instead of rebuilding.
     */
    private OrdersService resolveOrdersService(String userId) {
        CoinbaseAdvancedClient client = clientService.getClientForUser(userId);
        return orderServiceCache.computeIfAbsent(client, CoinbaseAdvancedServiceFactory::createOrdersService);
    }

    private Order fail(Order order, String reason, String detail) {
        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason(detail);
        orderRepository.save(order);

        OrderFailedEvent orderFailedEvent = new OrderFailedEvent();
        orderFailedEvent.symbol = order.getSymbol();
        orderFailedEvent.orderId = order.getId();
        orderFailedEvent.side = order.getSide();
        orderFailedEvent.baseSize = order.getBaseSize();
        orderFailedEvent.failureReason = detail;
        eventPublisher.publish(orderFailedEvent);

        ExecutionDegradedEvent degradedEvent = new ExecutionDegradedEvent();
        degradedEvent.symbol = order.getSymbol();
        degradedEvent.reason = reason;
        degradedEvent.detail = detail;
        eventPublisher.publish(degradedEvent);

        log.error("orderService LIVE ORDER FAILED symbol={} side={} baseSize={} reason={} detail={}",
                order.getSymbol(), order.getSide(), order.getBaseSize(), reason, detail);
        return order;
    }

    private String toSide(TradeDirection direction) {
        return switch (direction) {
            case LONG -> OrderConstants.SIDE_BUY;
            case SHORT -> OrderConstants.SIDE_SELL;
            case NONE -> null;
        };
    }

    private String toPlainString(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }
    
	private static Double findAvailableQtyForProduct(OrderRepository orderRepository, String productId) {
		Map<String, Double> result = getQtyBySideFromCache(orderRepository, productId);
		double buyQty = result.get(SIDE_BUY);
		double sellQty = result.get(SIDE_SELL);
		return buyQty - sellQty;
	}
	
	public static Map<String, Double> getQtyBySideFromCache(OrderRepository orderRepository, String productId) {
		Map<String, Double> result = new HashMap<>();
		List<Order> orders = orderRepository.findBySymbolOrderByCreatedAtDesc(productId);
		double buy = orders.stream().filter(o -> SIDE_BUY.equalsIgnoreCase(o.getSide())).mapToDouble(Order::getQty)
				.sum();
		double sell = orders.stream().filter(o -> SIDE_SELL.equalsIgnoreCase(o.getSide())).mapToDouble(Order::getQty)
				.sum();
		result.put(SIDE_BUY, buy);
		result.put(SIDE_SELL, sell);
		return result;
	}
}
