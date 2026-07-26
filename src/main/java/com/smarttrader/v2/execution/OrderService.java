package com.smarttrader.v2.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import com.coinbase.advanced.model.orders.MarketIoc;
import com.coinbase.advanced.model.orders.OrderConfiguration;
import com.coinbase.advanced.model.orders.TriggerGtc;
import com.coinbase.advanced.orders.OrdersService;
import com.smarttrader.v2.client.ClientService;
import com.smarttrader.v2.client.CoinbaseProperties;
import com.smarttrader.v2.constants.OrderConstants;
import com.smarttrader.v2.event.ExecutionDegradedEvent;
import com.smarttrader.v2.event.OrderFailedEvent;
import com.smarttrader.v2.event.OrderPlacedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Order;
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

        Order order = Order.builder()
                .symbol(symbol)
                .side(side)
                .orderType(OrderConstants.ORDER_TYPE_MARKET)
                .baseSize(decision.positionSize())
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
        return Optional.of(placeLive(order, USER_ID));
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
    public Optional<Order> placeOrder(String userId, Order orderRequest) {
        String effectiveUserId = (userId == null || userId.isBlank()) ? USER_ID : userId;

        if (orderRequest.getSymbol() == null || orderRequest.getSymbol().isBlank()
                || orderRequest.getSide() == null || orderRequest.getSide().isBlank()
                || orderRequest.getBaseSize() <= 0) {
            log.warn("orderService placeOrder userId={} rejected: symbol/side required and baseSize must be > 0",
                    effectiveUserId);
            return Optional.empty();
        }

        if (orderRequest.getClientOrderId() == null || orderRequest.getClientOrderId().isBlank()) {
            orderRequest.setClientOrderId(UUID.randomUUID().toString());
        }
        if (orderRequest.getOrderType() == null || orderRequest.getOrderType().isBlank()) {
            orderRequest.setOrderType(OrderConstants.ORDER_TYPE_MARKET);
        }
        if (orderRequest.getCreatedAtNs() == 0) {
            orderRequest.setCreatedAtNs(System.nanoTime());
        }
        if (orderRequest.getCreatedAt() == null) {
            orderRequest.setCreatedAt(LocalDateTime.now(ZoneId.of("America/New_York")));
        }

        if (isDuplicateOrder(orderRequest)) {
            log.warn("orderService placeOrder userId={} symbol={} side={} baseSize={} rejected: duplicate of a recent order",
                    effectiveUserId, orderRequest.getSymbol(), orderRequest.getSide(), orderRequest.getBaseSize());
            return Optional.empty();
        }

        log.info("orderService placeOrder userId={} symbol={} side={} baseSize={} (live-enabled={})",
                effectiveUserId, orderRequest.getSymbol(), orderRequest.getSide(), orderRequest.getBaseSize(), liveEnabled);

        if (!liveEnabled) {
            orderRequest.setDryRun(true);
            orderRequest.setStatus(OrderStatus.DRY_RUN);
            orderRepository.save(orderRequest);
            log.info("orderService DRY-RUN symbol={} side={} baseSize={} (live-enabled=false)",
                    orderRequest.getSymbol(), orderRequest.getSide(), orderRequest.getBaseSize());
            return Optional.of(orderRequest);
        }

        orderRequest.setDryRun(false);
        return Optional.of(placeLive(orderRequest, effectiveUserId));
    }

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

    private Order placeLive(Order order, String userId) {
        OrdersService ordersService;
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
                    .orderConfiguration(buildOrderConfiguration(order))
                    .retailPortfolioId(coinbaseProperties.portfolioId())
                    .build();

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

            return fail(order, "live order rejected by Coinbase", response.getFailureReason());
        } catch (CoinbaseAdvancedException e) {
            return fail(order, "live order submission failed (Coinbase API error)", e.getMessage());
        } catch (Exception e) {
            return fail(order, "live order submission threw an unexpected exception", e.getMessage());
        }
    }

    /**
     * Builds a trigger-bracket order when the order carries real stop/target levels
     * (SignalResult.stop()/target(), set on Order by execute() - see its javadoc), so the
     * live order enters with its stop-loss and take-profit already attached instead of
     * leaving the position unprotected until a separate exit order is placed. limitPrice
     * is the take-profit exit (targetPrice) and stopTriggerPrice is the stop-loss trigger
     * (stopPrice) - Coinbase infers exit direction from the order's own side, so a BUY
     * order's stopTriggerPrice must sit below entry and limitPrice above it (and the
     * reverse for SELL); sanityCheckBracket() rejects a bracket that doesn't satisfy that
     * before it's ever sent, since a swapped/nonsensical bracket would do the opposite of
     * "minimize losses."
     *
     * Falls back to a plain MARKET IOC order (no bracket) when stop/target aren't both
     * available and sane - e.g. placeOrder()'s manual path, which doesn't carry strategy
     * levels - rather than sending Coinbase a fabricated or malformed bracket.
     */
    private OrderConfiguration buildOrderConfiguration(Order order) {
        Double stop = order.getStopPrice();
        Double target = order.getTargetPrice();

        if (stop == null || target == null || stop <= 0 || target <= 0 || !sanityCheckBracket(order)) {
            log.info("orderService symbol={} side={} no usable stop/target levels, placing a plain MARKET order",
                    order.getSymbol(), order.getSide());
            return new OrderConfiguration.Builder()
                    .marketMarketIoc(new MarketIoc.Builder()
                            .baseSize(toPlainString(order.getBaseSize()))
                            .build())
                    .build();
        }

        log.info("orderService symbol={} side={} bracket stopTriggerPrice={} limitPrice={} (minimizing loss / locking gain)",
                order.getSymbol(), order.getSide(), toPlainString(stop), toPlainString(target));
        return new OrderConfiguration.Builder()
                .triggerBracketGtc(new TriggerGtc.Builder()
                        .baseSize(toPlainString(order.getBaseSize()))
                        .limitPrice(toPlainString(target))
                        .stopTriggerPrice(toPlainString(stop))
                        .build())
                .build();
    }

    /**
     * A BUY's stop-loss must sit below its take-profit (stop below entry limits the
     * downside; target above entry locks in the upside) - and the reverse for SELL. If
     * entryPrice is available, also requires stop/target to sit on the correct side of it.
     * Guards against exactly the kind of swapped/misordered levels that would turn a
     * "minimize losses" bracket into the opposite.
     */
    private boolean sanityCheckBracket(Order order) {
        double stop = order.getStopPrice();
        double target = order.getTargetPrice();
        Double entry = order.getEntryPrice();

        boolean isBuy = OrderConstants.SIDE_BUY.equalsIgnoreCase(order.getSide());
        boolean orderedCorrectly = isBuy ? stop < target : stop > target;
        if (!orderedCorrectly) {
            return false;
        }
        if (entry == null || entry <= 0) {
            return true;
        }
        return isBuy ? (stop < entry && target > entry) : (stop > entry && target < entry);
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

    private String toPlainString(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
