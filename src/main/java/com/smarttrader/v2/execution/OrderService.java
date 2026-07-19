package com.smarttrader.v2.execution;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.smarttrader.v2.client.CoinbaseOrdersClientFactory;
import com.smarttrader.v2.client.CoinbaseProperties;
import com.smarttrader.v2.constants.OrderConstants;
import com.smarttrader.v2.event.ExecutionDegradedEvent;
import com.smarttrader.v2.event.OrderFailedEvent;
import com.smarttrader.v2.event.OrderPlacedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
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
 * (CoinbaseOrdersClientFactory). If live mode is on but can't actually place a real order
 * for any reason - missing/invalid credentials, a Coinbase rejection, an exception - that
 * IS the system moving away from its stated target, so it publishes ExecutionDegradedEvent,
 * which NotificationFacadeService renders as a BOLD banner. Silence is never the failure
 * mode here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CoinbaseOrdersClientFactory ordersClientFactory;
    private final CoinbaseProperties coinbaseProperties;
    private final OrderRepository orderRepository;
    private final TradingEventPublisher eventPublisher;

    @Value("${smart-trader.execution.live-enabled:false}")
    private boolean liveEnabled;

    public Optional<Order> execute(TradeDecision decision, String symbol) {
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
                .createdAt(Instant.now())
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
