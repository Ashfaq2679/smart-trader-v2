package com.smarttrader.v2.execution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.coinbase.advanced.errors.CoinbaseAdvancedException;
import com.coinbase.advanced.model.orders.CreateOrderRequest;
import com.coinbase.advanced.model.orders.CreateOrderResponse;
import com.coinbase.advanced.model.orders.MarketIoc;
import com.coinbase.advanced.model.orders.OrderConfiguration;
import com.coinbase.advanced.orders.OrdersService;
import com.smarttrader.v2.client.CoinbaseOrdersClientFactoryV2;
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
 * Once live-enabled=true, a real MARKET order is placed via the official Coinbase SDK
 * (CoinbaseOrdersClientFactoryV2). If live mode is on but can't actually place a real order
 * for any reason - missing/invalid credentials, a Coinbase rejection, an exception - that
 * IS the system moving away from its stated target, so it publishes ExecutionDegradedEvent,
 * which NotificationFacadeService renders as a BOLD banner. Silence is never the failure
 * mode here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** Single-portfolio system, no multi-user support yet - see placeOrder()'s javadoc. */
    private static final String USER_ID = "ADMIN";

    private final CoinbaseOrdersClientFactoryV2 ordersClientFactory;
    private final CoinbaseProperties coinbaseProperties;
    private final OrderRepository orderRepository;
    private final TradingEventPublisher eventPublisher;

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
        return Optional.of(placeLive(order));
    }

    /**
     * Places a market order directly from a pre-built Order - a manual/API-triggered
     * placement path, as opposed to execute()'s TradeDecision-driven path. Reuses the
     * same dry-run/live logic as execute() (see placeLive()'s javadoc for what "live"
     * means) rather than duplicating it.
     *
     * Ported from smart-trader-v1's OrderService.placeOrder. v1's version routed through
     * a per-user OrdersService cache (ClientService/CoinbaseAdvancedClient keyed by
     * userId) and validated against a per-user funds/quantity ledger (UserService); v2 has
     * neither a multi-user credential store wired into order placement nor any funds/
     * position ledger (this codebase is single-portfolio - see the USER_ID constant), so
     * this places through the same single configured CoinbaseOrdersClientFactoryV2/
     * CoinbaseProperties as execute()/placeLive() do, and keeps the one piece of v1's
     * validation that translates cleanly to what v2 actually persists: rejecting an exact
     * duplicate of a very recent order (see isDuplicateOrder()).
     *
     * @param userId caller identity, logged only; not used to select credentials
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
        return Optional.of(placeLive(orderRequest));
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

    private Order placeLive(Order order) {
        Optional<OrdersService> ordersService = ordersClientFactory.create();
        if (ordersService.isEmpty()) {
            return fail(order, "missing order credentials",
                    "live-enabled=true but coinbase.api.key-name/private-key are not configured");
        }

        try {
            CreateOrderRequest request = new CreateOrderRequest.Builder()
                    .productId(order.getSymbol())
                    .side(order.getSide())
                    .clientOrderId(order.getClientOrderId())
                    .orderConfiguration(new OrderConfiguration.Builder()
                            .marketMarketIoc(new MarketIoc.Builder()
                                    .baseSize(toPlainString(order.getBaseSize()))
                                    .build())
                            .build())
                    .retailPortfolioId(coinbaseProperties.portfolioId())
                    .build();

            CreateOrderResponse response = ordersService.get().createOrder(request);

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

                log.info("orderService LIVE order placed symbol={} side={} baseSize={} coinbaseOrderId={}",
                        order.getSymbol(), order.getSide(), order.getBaseSize(), order.getCoinbaseOrderId());
                return order;
            }

            return fail(order, "live order rejected by Coinbase", response.getFailureReason());
        } catch (CoinbaseAdvancedException e) {
            return fail(order, "live order submission failed (Coinbase API error)", e.getMessage());
        } catch (Exception e) {
            return fail(order, "live order submission threw an unexpected exception", e.getMessage());
        }
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
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
