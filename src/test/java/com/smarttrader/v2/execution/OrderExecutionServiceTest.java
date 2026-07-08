package com.smarttrader.v2.execution;

import com.smarttrader.v2.event.DomainEventPublisher;
import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceTest {

    @Mock
    private IdempotencyKeyStore idempotencyKeyStore;
    @Mock
    private DomainEventPublisher eventPublisher;

    private OrderExecutionService orderExecutionService;

    @BeforeEach
    void setUp() {
        orderExecutionService = new OrderExecutionService(idempotencyKeyStore, eventPublisher);
    }

    private TradeDecision approvedDecision(double entry, Duration validityWindow) {
        SignalResult signal = SignalResult.builder()
                .valid(true)
                .strategyName("PullbackStrategy")
                .direction(TradeDirection.LONG)
                .entry(entry)
                .entryType(EntryType.LIMIT)
                .validityWindow(validityWindow)
                .stop(entry - 5)
                .target(entry + 10)
                .riskReward(2.0)
                .build();

        return TradeDecision.builder()
                .approved(true)
                .regime(MarketRegime.PULLBACK)
                .regimeConfidence(0.8)
                .signal(signal)
                .effectiveRiskReward(2.0)
                .positionSize(10)
                .reason("approved")
                .build();
    }

    @Test
    void bullish_placesOrderWhenWithinSlippageToleranceAndValidityWindow() {
        when(idempotencyKeyStore.find("key-1")).thenReturn(Optional.empty());
        TradeDecision decision = approvedDecision(100.0, Duration.ofMinutes(15));
        Instant generatedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = generatedAt.plusSeconds(30);

        OrderResult result = orderExecutionService.place(decision, "BTC-USD", 100.3, generatedAt, now, "key-1");

        assertThat(result.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(result.slippage()).isCloseTo(0.003, org.assertj.core.data.Offset.offset(0.0001));
        verify(idempotencyKeyStore).save(eq("key-1"), any());
    }

    @Test
    void bearish_rejectsWhenTradeDecisionWasNotApproved() {
        when(idempotencyKeyStore.find("key-2")).thenReturn(Optional.empty());
        TradeDecision rejected = TradeDecision.rejected(MarketRegime.BREAKOUT, SignalResult.invalid("BreakoutStrategy"), "below minimum R:R");

        OrderResult result = orderExecutionService.place(rejected, "BTC-USD", 100.0,
                Instant.now(), Instant.now(), "key-2");

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void sideways_cancelsWhenSlippageExceedsTolerance() {
        when(idempotencyKeyStore.find("key-3")).thenReturn(Optional.empty());
        TradeDecision decision = approvedDecision(100.0, Duration.ofMinutes(15));
        Instant generatedAt = Instant.parse("2026-01-01T00:00:00Z");

        OrderResult result = orderExecutionService.place(decision, "BTC-USD", 102.0,
                generatedAt, generatedAt.plusSeconds(5), "key-3");

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.reason()).contains("exceeds tolerance");
    }

    @Test
    void edgeCase_expiresWhenValidityWindowHasElapsed() {
        when(idempotencyKeyStore.find("key-4")).thenReturn(Optional.empty());
        TradeDecision decision = approvedDecision(100.0, Duration.ofMinutes(5));
        Instant generatedAt = Instant.parse("2026-01-01T00:00:00Z");

        OrderResult result = orderExecutionService.place(decision, "BTC-USD", 100.0,
                generatedAt, generatedAt.plus(Duration.ofMinutes(6)), "key-4");

        assertThat(result.status()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void edgeCase_duplicateIdempotencyKeyReturnsOriginalResultWithoutReevaluating() {
        OrderResult cached = OrderResult.builder().idempotencyKey("key-5").status(OrderStatus.PLACED).build();
        when(idempotencyKeyStore.find("key-5")).thenReturn(Optional.of(cached));
        TradeDecision decision = approvedDecision(100.0, Duration.ofMinutes(15));

        OrderResult result = orderExecutionService.place(decision, "BTC-USD", 999.0,
                Instant.now(), Instant.now(), "key-5");

        assertThat(result).isEqualTo(cached);
        verify(idempotencyKeyStore, never()).save(any(), any());
        verify(idempotencyKeyStore, times(1)).find("key-5");
    }

    @Test
    void edgeCase_blankIdempotencyKeyIsRejected() {
        TradeDecision decision = approvedDecision(100.0, Duration.ofMinutes(15));

        assertThatThrownBy(() -> orderExecutionService.place(decision, "BTC-USD", 100.0, Instant.now(), Instant.now(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
