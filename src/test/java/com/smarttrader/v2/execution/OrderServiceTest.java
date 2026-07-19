package com.smarttrader.v2.execution;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.coinbase.advanced.model.orders.CreateOrderResponse;
import com.coinbase.advanced.orders.OrdersService;
import com.smarttrader.v2.client.CoinbaseOrdersClientFactory;
import com.smarttrader.v2.client.CoinbaseProperties;
import com.smarttrader.v2.event.ExecutionDegradedEvent;
import com.smarttrader.v2.event.OrderFailedEvent;
import com.smarttrader.v2.event.OrderPlacedEvent;
import com.smarttrader.v2.event.TradingEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.Order;
import com.smarttrader.v2.model.OrderStatus;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private CoinbaseOrdersClientFactory ordersClientFactory;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradingEventPublisher eventPublisher;

    @Mock
    private OrdersService ordersService;

    private static final CoinbaseProperties PROPERTIES =
            new CoinbaseProperties("https://api.coinbase.com", "", "", "key", "pem", "portfolio-1");

    private OrderService service(boolean liveEnabled) {
        OrderService service = new OrderService(ordersClientFactory, PROPERTIES, orderRepository, eventPublisher);
        ReflectionTestUtils.setField(service, "liveEnabled", liveEnabled);
        return service;
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

        Optional<Order> result = service.execute(rejected, "BTC-USD");

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

        Optional<Order> result = service.execute(decision, "BTC-USD");

        assertThat(result).isEmpty();
        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void bullish_dryRunPersistsOrderWithoutTouchingCoinbaseOrPublishingEvents() {
        OrderService service = service(false);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Order> result = service.execute(approvedLong(), "BTC-USD");

        assertThat(result).isPresent();
        assertThat(result.get().isDryRun()).isTrue();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.DRY_RUN);
        assertThat(result.get().getSide()).isEqualTo("BUY");
        assertThat(result.get().getBaseSize()).isEqualTo(1.5);
        verify(orderRepository).save(any());
        verifyNoInteractions(ordersClientFactory, eventPublisher);
    }

    @Test
    void bullish_liveOrderPlacedSuccessfullyPublishesOrderPlacedEvent() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ordersClientFactory.create()).thenReturn(Optional.of(ordersService));
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(true).orderId("cb-123").build();
        when(ordersService.createOrder(any())).thenReturn(response);

        Optional<Order> result = service.execute(approvedLong(), "BTC-USD");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(result.get().getCoinbaseOrderId()).isEqualTo("cb-123");
        assertThat(result.get().isDryRun()).isFalse();

        ArgumentCaptor<TradingEvent> captor = ArgumentCaptor.forClass(TradingEvent.class);
        verify(eventPublisher, times(1)).publish(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(OrderPlacedEvent.class);
    }

    @Test
    void bearish_liveModeWithoutCredentialsFailsAndRaisesBoldAlert() {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ordersClientFactory.create()).thenReturn(Optional.empty());

        Optional<Order> result = service.execute(approvedLong(), "BTC-USD");

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
        when(ordersClientFactory.create()).thenReturn(Optional.of(ordersService));
        CreateOrderResponse response = new CreateOrderResponse.Builder().success(false).failureReason("insufficient funds").build();
        when(ordersService.createOrder(any())).thenReturn(response);

        Optional<Order> result = service.execute(approvedLong(), "BTC-USD");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.get().getFailureReason()).isEqualTo("insufficient funds");
        verify(eventPublisher, times(2)).publish(any());
    }

    @Test
    void edgeCase_unexpectedExceptionDuringSubmissionFailsAndRaisesBoldAlert() throws Exception {
        OrderService service = service(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ordersClientFactory.create()).thenReturn(Optional.of(ordersService));
        when(ordersService.createOrder(any())).thenThrow(new RuntimeException("network timeout"));

        Optional<Order> result = service.execute(approvedLong(), "BTC-USD");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.get().getFailureReason()).isEqualTo("network timeout");
        verify(eventPublisher, times(2)).publish(any());
    }
}
