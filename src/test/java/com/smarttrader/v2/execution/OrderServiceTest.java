package com.smarttrader.v2.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.factory.CoinbaseAdvancedServiceFactory;
import com.coinbase.advanced.model.orders.CreateOrderRequest;
import com.coinbase.advanced.model.orders.CreateOrderResponse;
import com.coinbase.advanced.model.orders.OrderConfiguration;
import com.coinbase.advanced.model.orders.TriggerGtc;
import com.coinbase.advanced.orders.OrdersService;
import com.smarttrader.v2.client.ClientService;
import com.smarttrader.v2.client.CoinbaseProperties;
import com.smarttrader.v2.event.ExecutionDegradedEvent;
import com.smarttrader.v2.event.OrderFailedEvent;
import com.smarttrader.v2.event.OrderPlacedEvent;
import com.smarttrader.v2.event.TradingEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.Order;
import com.smarttrader.v2.model.OrderStatus;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private ClientService clientService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradingEventPublisher eventPublisher;

    @Mock
    private CoinbaseAdvancedClient coinbaseAdvancedClient;

    @Mock
    private OrdersService ordersService;

    private static final CoinbaseProperties PROPERTIES =
            new CoinbaseProperties("https://api.coinbase.com", "", "", "key", "pem", "portfolio-1");

    private OrderService service(boolean liveEnabled) {
        OrderService service = new OrderService(clientService, PROPERTIES, orderRepository, eventPublisher);
        ReflectionTestUtils.setField(service, "liveEnabled", liveEnabled);
        return service;
    }

    /**
     * Stubs the same per-user client-creation path production code uses (see
     * OrderService.resolveOrdersService()): ClientService.getClientForUser(userId) ->
     * CoinbaseAdvancedClient -> CoinbaseAdvancedServiceFactory.createOrdersService(client).
     * The factory call is static (ported from smart-trader-v1's OrderHelper), so it's
     * mocked via Mockito's static mocking rather than an injectable seam.
     */
    private MockedStatic<CoinbaseAdvancedServiceFactory> stubOrdersServiceResolution(String userId) {
        when(clientService.getClientForUser(userId)).thenReturn(coinbaseAdvancedClient);
        MockedStatic<CoinbaseAdvancedServiceFactory> factory = mockStatic(CoinbaseAdvancedServiceFactory.class);
        factory.when(() -> CoinbaseAdvancedServiceFactory.createOrdersService(coinbaseAdvancedClient))
                .thenReturn(ordersService);
        return factory;
    }

    /** CreateOrderRequest exposes no public getter for orderConfiguration; read the field. */
    private OrderConfiguration orderConfigurationOf(CreateOrderRequest request) {
        return (OrderConfiguration) ReflectionTestUtils.getField(request, "orderConfiguration");
    }

    private TradeDecision approvedLong() {
        SignalResult signal = SignalResult.builder()
                .valid(true).strategyName("PullbackStrategy").direction(TradeDirection.LONG)
                .entry(100.0).stop(95.0).target(110.0).riskReward(2.0).build();
        return TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK)
                .signal(signal).positionSize(1.5).reason("approved").build();
    }

    @Test
    void bearish_unapprovedDecisionPlacesNothing() {
        OrderService service = service(false);
        TradeDecision rejected = TradeDecision.rejected(MarketRegime.PANIC, SignalResult.invalid("x"), "no signal");

        Optional<Order> result = service.execute(rejected, "BTC-USD", AnalysisContext.builder().build());

        assertThat(result).isEmpty();
        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void edgeCase_approvedDecisionWithNoneDirectionPlacesNothing() {
        OrderService service = service(false);
        SignalResult noneSignal = SignalResult.builder().valid(true).strategyName("x")
                .direction(TradeDirection.NONE).entry(1).stop(1).target(1).riskReward(2).build();
        TradeDecision decision = TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK)
                .signal(noneSignal).positionSize(1).reason("approved").build();

        Optional<Order> result = service.execute(decision, "BTC-USD", AnalysisContext.builder().build());

        assertThat(result).isEmpty();
        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void bullish_dryRunPersistsOrderWithoutTouchingCoinbaseOrPublishingEvents() {
        OrderService service = service(false);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Order> result = service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());

        assertThat(result).isPresent();
        assertThat(result.get().isDryRun()).isTrue();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.DRY_RUN);
        assertThat(result.get().getSide()).isEqualTo("BUY");
        assertThat(result.get().getBaseSize()).isEqualTo(1.5);
        verify(orderRepository).save(any());
        verifyNoInteractions(clientService, eventPublisher);
    }

    @Test
    void bullish_liveOrderPlacedSuccessfullyPublishesOrderPlacedEvent() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-123").build();
        when(ordersService.createOrder(any())).thenReturn(response);

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            Optional<Order> result = service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(OrderStatus.PLACED);
            assertThat(result.get().getCoinbaseOrderId()).isEqualTo("cb-123");
            assertThat(result.get().isDryRun()).isFalse();
        }

        ArgumentCaptor<TradingEvent> captor = ArgumentCaptor.forClass(TradingEvent.class);
        verify(eventPublisher, times(1)).publish(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(OrderPlacedEvent.class);
    }

    @Test
    void bullish_liveBuyOrderWithStopAndTargetPlacesATriggerBracketNotAPlainMarketOrder() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-1").build();
        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        when(ordersService.createOrder(requestCaptor.capture())).thenReturn(response);

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());
        }

        OrderConfiguration configuration = orderConfigurationOf(requestCaptor.getValue());
        assertThat(configuration.getMarketMarketIoc()).isNull();
        TriggerGtc bracket = configuration.getTriggerBracketGtc();
        assertThat(bracket).isNotNull();
        // approvedLong(): entry=100, stop=95, target=110 -> BUY bracket must stop below entry, limit above it.
        assertThat(bracket.getStopTriggerPrice()).isEqualTo("95.00");
        assertThat(bracket.getLimitPrice()).isEqualTo("110.00");
        assertThat(bracket.getBaseSize()).isEqualTo("1.50");
    }

    @Test
    void bearish_liveSellOrderWithStopAndTargetOrdersBracketForTheShortDirection() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-2").build();
        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        when(ordersService.createOrder(requestCaptor.capture())).thenReturn(response);

        SignalResult shortSignal = SignalResult.builder()
                .valid(true).strategyName("ShortSideStrategy").direction(TradeDirection.SHORT)
                .entry(100.0).stop(105.0).target(90.0).riskReward(2.0).build();
        TradeDecision shortDecision = TradeDecision.builder().approved(true).regime(MarketRegime.PANIC)
                .signal(shortSignal).positionSize(2.0).reason("approved").build();

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            service.execute(shortDecision, "BTC-USD", AnalysisContext.builder().build());
        }

        TriggerGtc bracket = orderConfigurationOf(requestCaptor.getValue()).getTriggerBracketGtc();
        assertThat(bracket).isNotNull();
        // SELL bracket must stop above entry, limit below it.
        assertThat(bracket.getStopTriggerPrice()).isEqualTo("105.00");
        assertThat(bracket.getLimitPrice()).isEqualTo("90.00");
    }

    @Test
    void edgeCase_liveOrderWithoutUsableStopTargetFallsBackToPlainMarketOrder() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findBySymbolOrderByCreatedAtDesc("BTC-USD")).thenReturn(List.of());
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-3").build();
        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        when(ordersService.createOrder(requestCaptor.capture())).thenReturn(response);

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            service.placeOrder("ADMIN", manualOrder("BUY", 1.0));
        }

        OrderConfiguration configuration = orderConfigurationOf(requestCaptor.getValue());
        assertThat(configuration.getTriggerBracketGtc()).isNull();
        assertThat(configuration.getMarketMarketIoc()).isNotNull();
        assertThat(configuration.getMarketMarketIoc().getBaseSize()).isEqualTo("1.00");
    }

    @Test
    void edgeCase_liveOrderWithSwappedStopAndTargetFallsBackToPlainMarketOrder() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-4").build();
        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        when(ordersService.createOrder(requestCaptor.capture())).thenReturn(response);

        // A BUY with stop ABOVE target is nonsensical (would maximize loss, not minimize it).
        SignalResult brokenSignal = SignalResult.builder()
                .valid(true).strategyName("PullbackStrategy").direction(TradeDirection.LONG)
                .entry(100.0).stop(110.0).target(95.0).riskReward(2.0).build();
        TradeDecision brokenDecision = TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK)
                .signal(brokenSignal).positionSize(1.0).reason("approved").build();

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            service.execute(brokenDecision, "BTC-USD", AnalysisContext.builder().build());
        }

        OrderConfiguration configuration = orderConfigurationOf(requestCaptor.getValue());
        assertThat(configuration.getTriggerBracketGtc()).isNull();
        assertThat(configuration.getMarketMarketIoc()).isNotNull();
    }

    @Test
    void bearish_liveModeWithoutCredentialsFailsAndRaisesBoldAlert() {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clientService.getClientForUser("ADMIN"))
                .thenThrow(new IllegalArgumentException("No credentials found for user: ADMIN"));

        Optional<Order> result = service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.FAILED);

        ArgumentCaptor<TradingEvent> captor = ArgumentCaptor.forClass(TradingEvent.class);
        verify(eventPublisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).hasAtLeastOneElementOfType(OrderFailedEvent.class);
        assertThat(captor.getAllValues()).hasAtLeastOneElementOfType(ExecutionDegradedEvent.class);
    }

    @Test
    void bearish_coinbaseRejectionFailsAndRaisesBoldAlert() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(false).failureReason("insufficient funds").build();
        when(ordersService.createOrder(any())).thenReturn(response);

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            Optional<Order> result = service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(result.get().getFailureReason()).isEqualTo("insufficient funds");
        }
        verify(eventPublisher, times(2)).publish(any());
    }

    @Test
    void edgeCase_unexpectedExceptionDuringSubmissionFailsAndRaisesBoldAlert() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ordersService.createOrder(any())).thenThrow(new RuntimeException("network timeout"));

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            Optional<Order> result = service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(result.get().getFailureReason()).isEqualTo("network timeout");
        }
        verify(eventPublisher, times(2)).publish(any());
    }

    private Order manualOrder(String side, double baseSize) {
        return Order.builder().symbol("BTC-USD").side(side).baseSize(baseSize).build();
    }

    @Test
    void bullish_placeOrderDryRunPersistsOrderForGivenUser() {
        OrderService service = service(false);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findBySymbolOrderByCreatedAtDesc("BTC-USD")).thenReturn(List.of());

        Optional<Order> result = service.placeOrder("trader-1", manualOrder("BUY", 2.0));

        assertThat(result).isPresent();
        assertThat(result.get().isDryRun()).isTrue();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.DRY_RUN);
        assertThat(result.get().getClientOrderId()).isNotBlank();
        assertThat(result.get().getOrderType()).isEqualTo("MARKET");
        verifyNoInteractions(clientService, eventPublisher);
    }

    @Test
    void bullish_placeOrderLiveResolvesCredentialsForTheGivenUserNotAdmin() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findBySymbolOrderByCreatedAtDesc("BTC-USD")).thenReturn(List.of());
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-456").build();
        when(ordersService.createOrder(any())).thenReturn(response);

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("trader-1")) {
            Optional<Order> result = service.placeOrder("trader-1", manualOrder("BUY", 2.0));

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(OrderStatus.PLACED);
        }
        verify(clientService).getClientForUser("trader-1");
    }

    @Test
    void bearish_placeOrderWithMissingSideIsRejectedWithoutTouchingRepository() {
        OrderService service = service(false);

        Optional<Order> result = service.placeOrder("trader-1", manualOrder(null, 2.0));

        assertThat(result).isEmpty();
        verifyNoInteractions(orderRepository, eventPublisher, clientService);
    }

    @Test
    void bearish_placeOrderWithNonPositiveBaseSizeIsRejected() {
        OrderService service = service(false);

        Optional<Order> result = service.placeOrder("trader-1", manualOrder("BUY", 0.0));

        assertThat(result).isEmpty();
        verifyNoInteractions(orderRepository, eventPublisher, clientService);
    }

    @Test
    void edgeCase_placeOrderRejectsAnExactDuplicateFromTheSameMinute() {
        OrderService service = service(false);
        Order recent = Order.builder()
                .symbol("BTC-USD").side("BUY").baseSize(2.0)
                .createdAt(java.time.LocalDateTime.now(java.time.ZoneId.of("America/New_York")))
                .build();
        when(orderRepository.findBySymbolOrderByCreatedAtDesc("BTC-USD")).thenReturn(List.of(recent));

        Optional<Order> result = service.placeOrder("trader-1", manualOrder("BUY", 2.0));

        assertThat(result).isEmpty();
        verify(orderRepository, times(0)).save(any());
    }

    @Test
    void edgeCase_placeOrderWithNullUserIdFallsBackToDefaultUser() {
        OrderService service = service(false);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findBySymbolOrderByCreatedAtDesc("BTC-USD")).thenReturn(List.of());

        Optional<Order> result = service.placeOrder(null, manualOrder("SELL", 1.0));

        assertThat(result).isPresent();
        assertThat(result.get().getSide()).isEqualTo("SELL");
    }
}
