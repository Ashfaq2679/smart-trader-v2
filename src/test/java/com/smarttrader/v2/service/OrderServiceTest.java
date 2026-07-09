package com.smarttrader.v2.service;

import com.coinbase.advanced.model.orders.CancelOrdersRequest;
import com.coinbase.advanced.model.orders.CancelOrdersResponse;
import com.coinbase.advanced.model.orders.CancelResult;
import com.coinbase.advanced.model.orders.CreateOrderResponse;
import com.coinbase.advanced.model.orders.SuccessResponse;
import com.coinbase.advanced.orders.OrdersService;
import com.smarttrader.v2.helper.OrderHelper;
import com.smarttrader.v2.model.Order;
import com.smarttrader.v2.model.OrderRequest;
import com.smarttrader.v2.model.OrderResponse;
import com.smarttrader.v2.model.User;
import com.smarttrader.v2.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private ClientService clientService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserService userService;
    @Mock
    private OrdersService ordersService;

    private OrderService orderService;
    private MockedStatic<OrderHelper> orderHelperStatic;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(clientService, orderRepository, userService);
        ReflectionTestUtils.setField(orderService, "portfolioId", "test-portfolio");

        // OrderHelper.getOrderServiceFromCache talks to the real Coinbase SDK factory,
        // which would try real network calls; stub only that seam and let the rest of
        // OrderHelper's pure logic (buildOrderConfiguration, getQtyBySideFromCache, etc.)
        // run for real so we're exercising OrderService's actual orchestration.
        orderHelperStatic = mockStatic(OrderHelper.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        orderHelperStatic.when(() -> OrderHelper.getOrderServiceFromCache(eq(clientService), any(), any()))
                .thenReturn(ordersService);
    }

    @AfterEach
    void tearDown() {
        orderHelperStatic.close();
    }

    private User userWithFunds(double funds) {
        User user = new User();
        user.setUserName("trader-1");
        user.setCurrentFunds(funds);
        return user;
    }

    private OrderRequest limitBuyRequest() {
        return OrderRequest.builder().productId("BTC-USD").side("BUY")
                .orderType("LIMIT").baseSize(0.001).limitPrice(50_000.0).build();
    }

    // --- placeOrder ---

    @Test
    void bullish_placesOrderAndPersistsItAndUpdatesFunds() {
        // "ADMIN" is the hardcoded userName validateOrderRequest()'s internal funds check
        // uses (independent of the actual placing userId) — pre-existing behavior, not
        // something this test is trying to change.
        when(userService.findByUserName("ADMIN")).thenReturn(userWithFunds(10_000));
        when(userService.findByUserName("trader-1")).thenReturn(userWithFunds(10_000));
        CreateOrderResponse response = successResponse("cb-order-1");
        when(ordersService.createOrder(any())).thenReturn(response);

        OrderResponse result = orderService.placeOrder("trader-1", limitBuyRequest());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCoinbaseOrderId()).isEqualTo("cb-order-1");
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getUserId()).isEqualTo("trader-1");
        verify(userService).updateUserFunds(eq("trader-1"), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void bearish_invalidRequestIsRejectedWithoutCallingExchange() {
        OrderRequest invalid = OrderRequest.builder().side("BUY").orderType("LIMIT").build(); // missing productId

        OrderResponse result = orderService.placeOrder("trader-1", invalid);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReason()).contains("productId is required");
        verify(ordersService, never()).createOrder(any());
    }

    @Test
    void bearish_duplicateOrderWithinLastMinuteIsRejected() {
        when(userService.findByUserName("ADMIN")).thenReturn(userWithFunds(10_000));
        // decideOrderQty(50000) = DEFAULT_ORDER_VALUE_IN_USD (50) / 50000 = 0.001, matching
        // the synthetic validation order validateOrderRequest() builds internally.
        Order recentDuplicate = new Order();
        recentDuplicate.setCreatedAt(java.time.LocalDateTime.now());
        recentDuplicate.setQty(0.001);
        recentDuplicate.setLimitPrice(50_000.0);
        when(orderRepository.findByProductIdAndSide("BTC-USD", "BUY")).thenReturn(List.of(recentDuplicate));

        OrderResponse result = orderService.placeOrder("trader-1", limitBuyRequest());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReason()).contains("Duplicate order detected");
        verify(ordersService, never()).createOrder(any());
    }

    @Test
    void sideways_sellWithInsufficientAvailableQuantityIsRejected() {
        when(userService.findByUserName("ADMIN")).thenReturn(userWithFunds(10_000));
        // No prior BUY orders recorded -> available qty is 0, so any SELL should be rejected.
        when(orderRepository.findByProductId("BTC-USD")).thenReturn(List.of());
        OrderRequest sellRequest = OrderRequest.builder().productId("BTC-USD").side("SELL")
                .orderType("LIMIT").baseSize(0.001).limitPrice(50_000.0).build();

        OrderResponse result = orderService.placeOrder("trader-1", sellRequest);

        assertThat(result.isSuccess()).isFalse();
        verify(ordersService, never()).createOrder(any());
    }

    @Test
    void edgeCase_noCoinbaseClientForUserFailsGracefullyWithoutNpe() {
        when(userService.findByUserName("ADMIN")).thenReturn(userWithFunds(10_000));
        orderHelperStatic.when(() -> OrderHelper.getOrderServiceFromCache(eq(clientService), eq("trader-2"), any()))
                .thenReturn(null);

        OrderResponse result = orderService.placeOrder("trader-2", limitBuyRequest());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReason()).contains("no Coinbase credentials");
    }

    // --- cancelOrder ---

    @Test
    void cancelOrderSuccessUpdatesLocalStatus() {
        Order order = new Order();
        order.setId("order-1");
        order.setUserId("trader-1");
        order.setCoinbaseOrderId("cb-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        CancelResult cancelResult = new CancelResult();
        cancelResult.setSuccess(true);
        cancelResult.setOrderId("cb-1");
        CancelOrdersResponse cancelResponse = new CancelOrdersResponse();
        cancelResponse.setResults(List.of(cancelResult));
        when(ordersService.cancelOrders(any(CancelOrdersRequest.class))).thenReturn(cancelResponse);

        OrderResponse result = orderService.cancelOrder("trader-1", "order-1");

        assertThat(result.isSuccess()).isTrue();
        verify(orderRepository).save(order);
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelOrderRejectsWhenOrderBelongsToAnotherUser() {
        Order order = new Order();
        order.setId("order-1");
        order.setUserId("someone-else");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> orderService.cancelOrder("trader-1", "order-1"));
    }

    // --- updateOrderStatusFromExchange ---

    @Test
    void updateOrderStatusFromExchangeSkipsOrdersAlreadyInATerminalState() {
        Order filled = new Order();
        filled.setStatus("FILLED");
        filled.setCoinbaseOrderId("cb-filled");
        Order cancelled = new Order();
        cancelled.setStatus("CANCELLED");
        cancelled.setCoinbaseOrderId("cb-cancelled");
        Order open = new Order();
        open.setStatus("OPEN");
        open.setCoinbaseOrderId("cb-open");
        when(orderRepository.findAll()).thenReturn(List.of(filled, cancelled, open));
        when(ordersService.getOrder(any())).thenReturn(null);

        orderService.updateOrderStatusFromExchange();

        // Only the non-terminal order should trigger a lookup against the exchange.
        verify(ordersService, times(1)).getOrder(any());
    }

    private CreateOrderResponse successResponse(String coinbaseOrderId) {
        SuccessResponse successResponse = new SuccessResponse();
        successResponse.setOrderId(coinbaseOrderId);
        return new CreateOrderResponse.Builder()
                .success(true)
                .orderId(coinbaseOrderId)
                .successResponse(successResponse)
                .build();
    }
}
