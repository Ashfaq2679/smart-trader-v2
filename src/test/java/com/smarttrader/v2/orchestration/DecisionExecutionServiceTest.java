package com.smarttrader.v2.orchestration;

import com.smarttrader.v2.execution.OrderExecutionService;
import com.smarttrader.v2.execution.OrderResult;
import com.smarttrader.v2.execution.OrderStatus;
import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.OrderRequest;
import com.smarttrader.v2.model.OrderResponse;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.position.Position;
import com.smarttrader.v2.position.PositionService;
import com.smarttrader.v2.position.PositionStatus;
import com.smarttrader.v2.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionExecutionServiceTest {

    @Mock
    private OrderExecutionService orderExecutionService;
    @Mock
    private OrderService orderService;
    @Mock
    private PositionService positionService;

    private DecisionExecutionService decisionExecutionService;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        decisionExecutionService = new DecisionExecutionService(orderExecutionService, orderService, positionService, FIXED_CLOCK);
    }

    private TradeDecision approvedDecision(TradeDirection direction) {
        SignalResult signal = SignalResult.builder().valid(true).strategyName("PullbackStrategy")
                .direction(direction).entry(100.0).entryType(EntryType.LIMIT).validityWindow(Duration.ofMinutes(15))
                .stop(95.0).target(110.0).riskReward(2.0).build();
        return TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK).regimeConfidence(0.8)
                .signal(signal).effectiveRiskReward(2.0).positionSize(10).reason("approved").build();
    }

    private OrderResult placedRealism() {
        return OrderResult.builder().idempotencyKey("corr-1").productId("BTC-USD")
                .status(OrderStatus.PLACED).reason("placed").direction(TradeDirection.LONG)
                .requestedPrice(100.0).quotedPrice(100.1).slippage(0.001).positionSize(10)
                .evaluatedAt(Instant.now(FIXED_CLOCK)).build();
    }

    @Test
    void bullish_approvedLongDecisionThatPassesRealismChecksPlacesABuyOrderAndOpensAPosition() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG);
        when(orderExecutionService.place(eq(decision), eq("BTC-USD"), eq(100.1), any(), any(), eq("corr-1"), eq("corr-1")))
                .thenReturn(placedRealism());
        OrderResponse response = OrderResponse.builder().success(true).coinbaseOrderId("cb-1").build();
        when(orderService.placeOrder(eq("trader-1"), any(OrderRequest.class))).thenReturn(response);

        Optional<OrderResponse> result = decisionExecutionService.execute(decision, "BTC-USD", "trader-1", 100.1, "corr-1");

        assertThat(result).contains(response);
        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(orderService).placeOrder(eq("trader-1"), captor.capture());
        assertThat(captor.getValue().getSide()).isEqualTo("BUY");
        assertThat(captor.getValue().getOrderType()).isEqualTo("LIMIT");
        assertThat(captor.getValue().getBaseSize()).isEqualTo(10.0);
        verify(positionService).open(eq(decision), eq("BTC-USD"), eq("cb-1"), any(), eq("corr-1"));
    }

    @Test
    void bearish_approvedShortDecisionPlacesASellOrder() {
        TradeDecision decision = approvedDecision(TradeDirection.SHORT);
        when(orderExecutionService.place(eq(decision), eq("BTC-USD"), eq(100.1), any(), any(), eq("corr-2"), eq("corr-2")))
                .thenReturn(placedRealism());
        when(orderService.placeOrder(eq("trader-1"), any(OrderRequest.class)))
                .thenReturn(OrderResponse.builder().success(true).coinbaseOrderId("cb-2").build());

        decisionExecutionService.execute(decision, "BTC-USD", "trader-1", 100.1, "corr-2");

        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(orderService).placeOrder(eq("trader-1"), captor.capture());
        assertThat(captor.getValue().getSide()).isEqualTo("SELL");
    }

    @Test
    void sideways_unapprovedDecisionNeverReachesOrderExecutionOrOrderService() {
        TradeDecision rejected = TradeDecision.rejected(MarketRegime.PANIC, SignalResult.invalid("none"), "no strategy defined");

        Optional<OrderResponse> result = decisionExecutionService.execute(rejected, "BTC-USD", "trader-1", 100.0, "corr-3");

        assertThat(result).isEmpty();
        verify(orderExecutionService, never()).place(any(), anyString(), any(Double.class), any(), any(), anyString(), anyString());
        verify(orderService, never()).placeOrder(anyString(), any());
    }

    @Test
    void edgeCase_realismCheckRejectsSlippageSoOrderServiceIsNeverCalled() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG);
        OrderResult cancelled = OrderResult.builder().status(OrderStatus.CANCELLED).reason("slippage too high").build();
        when(orderExecutionService.place(eq(decision), eq("BTC-USD"), eq(105.0), any(), any(), eq("corr-4"), eq("corr-4")))
                .thenReturn(cancelled);

        Optional<OrderResponse> result = decisionExecutionService.execute(decision, "BTC-USD", "trader-1", 105.0, "corr-4");

        assertThat(result).isEmpty();
        verify(orderService, never()).placeOrder(anyString(), any());
    }

    @Test
    void edgeCase_failedOrderPlacementDoesNotOpenAPosition() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG);
        when(orderExecutionService.place(eq(decision), eq("BTC-USD"), eq(100.1), any(), any(), eq("corr-5"), eq("corr-5")))
                .thenReturn(placedRealism());
        when(orderService.placeOrder(eq("trader-1"), any(OrderRequest.class)))
                .thenReturn(OrderResponse.builder().success(false).failureReason("insufficient funds").build());

        decisionExecutionService.execute(decision, "BTC-USD", "trader-1", 100.1, "corr-5");

        verify(positionService, never()).open(any(), anyString(), anyString(), any(), anyString());
    }
}
