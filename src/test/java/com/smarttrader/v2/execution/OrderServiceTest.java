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
import com.coinbase.advanced.model.orders.ErrorResponse;
import com.coinbase.advanced.model.orders.LimitGtc;
import com.coinbase.advanced.model.orders.OrderConfiguration;
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

/**
 * Covers OrderService as it's actually wired today: execute() always builds a LIMIT,
 * post-only order (orderType is hardcoded "LIMIT" and stopLoss/takeProfit are not set),
 * sized to exactly OrderConstants.FIXED_ORDER_VALUE_USD regardless of what RiskEngine's
 * positionSize() computed. placeOrder() is currently commented out in OrderService, so
 * it has no tests here - re-add them if/when it's re-enabled.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final double FIXED_ORDER_VALUE_USD = com.smarttrader.v2.constants.OrderConstants.FIXED_ORDER_VALUE_USD;

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

    private TradeDecision approvedShort() {
        SignalResult signal = SignalResult.builder()
                .valid(true).strategyName("ShortSideStrategy").direction(TradeDirection.SHORT)
                .entry(100.0).stop(105.0).target(90.0).riskReward(2.0).build();
        return TradeDecision.builder().approved(true).regime(MarketRegime.PANIC)
                .signal(signal).positionSize(2.0).reason("approved").build();
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
    void edgeCase_approvedDecisionWithNonPositiveEntryPricePlacesNothing() {
        OrderService service = service(false);
        SignalResult zeroEntrySignal = SignalResult.builder().valid(true).strategyName("PullbackStrategy")
                .direction(TradeDirection.LONG).entry(0.0).stop(95.0).target(110.0).riskReward(2.0).build();
        TradeDecision decision = TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK)
                .signal(zeroEntrySignal).positionSize(1.5).reason("approved").build();

        Optional<Order> result = service.execute(decision, "BTC-USD", AnalysisContext.builder().build());

        assertThat(result).isEmpty();
        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void bullish_dryRunPersistsOrderSizedToExactlyElevenDollars() {
        OrderService service = service(false);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Order> result = service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());

        assertThat(result).isPresent();
        assertThat(result.get().isDryRun()).isTrue();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.DRY_RUN);
        assertThat(result.get().getSide()).isEqualTo("BUY");
        // entry=100 -> baseSize = $11 / 100 = 0.11, not RiskEngine's positionSize (1.5).
        assertThat(result.get().getBaseSize()).isEqualTo(FIXED_ORDER_VALUE_USD / 100.0);
        verify(orderRepository).save(any());
        verifyNoInteractions(clientService, eventPublisher);
    }

    @Test
    void bullish_liveBuyOrderPlacesAPostOnlyLimitOrderSizedToElevenDollars() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-123").build();
        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        when(ordersService.createOrder(requestCaptor.capture())).thenReturn(response);

        Optional<Order> result;
        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            result = service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());
        }

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(result.get().getCoinbaseOrderId()).isEqualTo("cb-123");
        assertThat(result.get().getBaseSize()).isEqualTo(FIXED_ORDER_VALUE_USD / 100.0);

        OrderConfiguration configuration = orderConfigurationOf(requestCaptor.getValue());
        LimitGtc limit = configuration.getLimitLimitGtc();
        assertThat(limit).isNotNull();
        assertThat(configuration.getMarketMarketIoc()).isNull();
        assertThat(configuration.getTriggerBracketGtc()).isNull();
        assertThat(limit.isPostOnly()).isTrue();
        // entry=100, BUY nudges limitPrice down 0.1% -> 99.90; baseSize stays $11/100 = 0.110.
        assertThat(limit.getLimitPrice()).isEqualTo("99.90");
        assertThat(limit.getBaseSize()).isEqualTo("0.110");

        ArgumentCaptor<TradingEvent> eventCaptor = ArgumentCaptor.forClass(TradingEvent.class);
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(OrderPlacedEvent.class);
    }

    @Test
    void bearish_liveSellOrderNudgesLimitPriceUpAndKeepsElevenDollarSize() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findBySymbolOrderByCreatedAtDesc("BTC-USD")).thenReturn(List.of());
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-456").build();
        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        when(ordersService.createOrder(requestCaptor.capture())).thenReturn(response);

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            service.execute(approvedShort(), "BTC-USD", AnalysisContext.builder().build());
        }

        LimitGtc limit = orderConfigurationOf(requestCaptor.getValue()).getLimitLimitGtc();
        assertThat(limit).isNotNull();
        // entry=100, SELL nudges limitPrice up 0.5% -> 100.50; no prior holdings recorded so
        // the $11-derived size (0.110) isn't shrunk.
        assertThat(limit.getLimitPrice()).isEqualTo("100.50");
        assertThat(limit.getBaseSize()).isEqualTo("0.110");
    }

    @Test
    void edgeCase_liveSellOrderShrinksBelowElevenDollarsWhenHoldingsAreInsufficient() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Order priorBuy = Order.builder().symbol("BTC-USD").side("BUY").qty(0.05).build();
        when(orderRepository.findBySymbolOrderByCreatedAtDesc("BTC-USD")).thenReturn(List.of(priorBuy));
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-789").build();
        ArgumentCaptor<CreateOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        when(ordersService.createOrder(requestCaptor.capture())).thenReturn(response);

        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            service.execute(approvedShort(), "BTC-USD", AnalysisContext.builder().build());
        }

        // $11/100 = 0.110 wanted, but only 0.05 is actually held -> can't sell more than owned.
        LimitGtc limit = orderConfigurationOf(requestCaptor.getValue()).getLimitLimitGtc();
        assertThat(limit.getBaseSize()).isEqualTo("0.050");
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
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setMessage("insufficient funds");
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(false).errorResponse(errorResponse).build();
        when(ordersService.createOrder(any())).thenReturn(response);

        Optional<Order> result;
        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            result = service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());
        }

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.get().getFailureReason()).isEqualTo("insufficient funds");
        verify(eventPublisher, times(2)).publish(any());
    }

    @Test
    void edgeCase_unexpectedExceptionDuringSubmissionFailsAndRaisesBoldAlert() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ordersService.createOrder(any())).thenThrow(new RuntimeException("network timeout"));

        Optional<Order> result;
        try (MockedStatic<CoinbaseAdvancedServiceFactory> ignored = stubOrdersServiceResolution("ADMIN")) {
            result = service.execute(approvedLong(), "BTC-USD", AnalysisContext.builder().build());
        }

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.get().getFailureReason()).isEqualTo("network timeout");
        verify(eventPublisher, times(2)).publish(any());
    }

    /**
     * buildOrderConfiguration()'s MARKET branch isn't reachable via execute() today
     * (orderType is hardcoded "LIMIT" there), but it's still real code - exercised
     * directly here via reflection since it's private.
     */
    @Test
    void bullish_marketSellSizesBaseSizeFromCurrentPriceToHitElevenDollars() {
        OrderService service = service(false);
        com.smarttrader.v2.model.OrderRequest request = com.smarttrader.v2.model.OrderRequest.builder()
                .productId("BTC-USD")
                .side("SELL")
                .orderType("MARKET")
                .baseSize(0.05) // stale value sized off a different (signal) price
                .entryPriceNum(200.0) // current price at request time
                .build();

        OrderConfiguration config = (OrderConfiguration) ReflectionTestUtils.invokeMethod(service, "buildOrderConfiguration", request);

        assertThat(config.getMarketMarketIoc().getBaseSize()).isEqualTo("0.055");
    }

    @Test
    void edgeCase_marketSellFallsBackToLimitPriceWhenCurrentPriceIsMissing() {
        OrderService service = service(false);
        com.smarttrader.v2.model.OrderRequest request = com.smarttrader.v2.model.OrderRequest.builder()
                .productId("BTC-USD")
                .side("SELL")
                .orderType("MARKET")
                .baseSize(0.05)
                .limitPrice(110.0)
                .build();

        OrderConfiguration config = (OrderConfiguration) ReflectionTestUtils.invokeMethod(service, "buildOrderConfiguration", request);

        assertThat(config.getMarketMarketIoc().getBaseSize()).isEqualTo("0.100");
    }

    @Test
    void bullish_marketBuySizesByExactQuoteAmount() {
        OrderService service = service(false);
        com.smarttrader.v2.model.OrderRequest request = com.smarttrader.v2.model.OrderRequest.builder()
                .productId("BTC-USD")
                .side("BUY")
                .orderType("MARKET")
                .baseSize(0.05)
                .entryPriceNum(200.0)
                .build();

        OrderConfiguration config = (OrderConfiguration) ReflectionTestUtils.invokeMethod(service, "buildOrderConfiguration", request);

        assertThat(config.getMarketMarketIoc().getQuoteSize()).isEqualTo("11.00");
    }
}
