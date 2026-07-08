package com.smarttrader.v2.portfolio;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.event.DomainEventPublisher;
import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.position.Position;
import com.smarttrader.v2.position.PositionService;
import com.smarttrader.v2.position.PositionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioRiskServiceTest {

    @Mock
    private PositionService positionService;
    @Mock
    private CorrelationTracker correlationTracker;
    @Mock
    private DomainEventPublisher eventPublisher;

    private PortfolioRiskService portfolioRiskService;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        portfolioRiskService = new PortfolioRiskService(positionService, correlationTracker, eventPublisher);
    }

    private TradeDecision approvedDecision(double entry, double positionSize) {
        SignalResult signal = SignalResult.builder()
                .valid(true).strategyName("PullbackStrategy").direction(TradeDirection.LONG)
                .entry(entry).entryType(EntryType.LIMIT).validityWindow(Duration.ofMinutes(15))
                .stop(entry - 5).target(entry + 10).riskReward(2.0).build();
        return TradeDecision.builder().approved(true).regime(MarketRegime.PULLBACK).regimeConfidence(0.8)
                .signal(signal).effectiveRiskReward(2.0).positionSize(positionSize).reason("approved").build();
    }

    private Position openPosition(String productId, double requestedSize, double entryPrice) {
        return Position.builder().positionId("pos-" + productId).productId(productId)
                .direction(TradeDirection.LONG).entryPrice(entryPrice).stopPrice(entryPrice - 5)
                .targetPrice(entryPrice + 10).requestedSize(requestedSize).filledSize(requestedSize)
                .status(PositionStatus.OPEN).openedAt(now).build();
    }

    @Test
    void bullish_noOpenPositionsAndWithinExposureLimitLeavesDecisionUnchanged() {
        when(positionService.findAll()).thenReturn(List.of());
        TradeDecision decision = approvedDecision(100.0, 10.0);

        TradeDecision result = portfolioRiskService.apply(decision, "BTC-USD", 10_000, "corr-1", now);

        assertThat(result.approved()).isTrue();
        assertThat(result.positionSize()).isEqualTo(10.0);
        verify(eventPublisher).publish(any());
    }

    @Test
    void bearish_rejectsWhenPortfolioExposureLimitAlreadyReached() {
        // maxExposure = 10_000 * 0.20 = 2000; one open position already at 2000 notional
        when(positionService.findAll()).thenReturn(List.of(openPosition("ETH-USD", 20, 100.0)));
        TradeDecision decision = approvedDecision(100.0, 10.0);

        TradeDecision result = portfolioRiskService.apply(decision, "BTC-USD", 10_000, "corr-2", now);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("exposure limit reached");
    }

    @Test
    void sideways_unapprovedDecisionPassesThroughUnchanged() {
        when(positionService.findAll()).thenReturn(List.of());
        TradeDecision rejected = TradeDecision.rejected(MarketRegime.BREAKOUT, SignalResult.invalid("BreakoutStrategy"), "below minimum R:R");

        TradeDecision result = portfolioRiskService.apply(rejected, "BTC-USD", 10_000, "corr-3", now);

        assertThat(result).isEqualTo(rejected);
    }

    @Test
    void edgeCase_highlyCorrelatedOpenPositionReducesAdaptivePositionSize() {
        when(positionService.findAll()).thenReturn(List.of(openPosition("ETH-USD", 1, 100.0)));
        when(correlationTracker.correlation("BTC-USD", "ETH-USD")).thenReturn(Optional.of(0.9));
        TradeDecision decision = approvedDecision(100.0, 10.0);

        TradeDecision result = portfolioRiskService.apply(decision, "BTC-USD", 10_000, "corr-4", now);

        assertThat(result.approved()).isTrue();
        assertThat(result.positionSize()).isEqualTo(10.0 * TradingConstants.CORRELATION_SIZE_REDUCTION_FACTOR);
    }

    @Test
    void edgeCase_lowCorrelationDoesNotReducePositionSize() {
        when(positionService.findAll()).thenReturn(List.of(openPosition("ETH-USD", 1, 100.0)));
        when(correlationTracker.correlation("BTC-USD", "ETH-USD")).thenReturn(Optional.of(0.2));
        TradeDecision decision = approvedDecision(100.0, 10.0);

        TradeDecision result = portfolioRiskService.apply(decision, "BTC-USD", 10_000, "corr-5", now);

        assertThat(result.positionSize()).isEqualTo(10.0);
    }

    @Test
    void edgeCase_sizeCappedByRemainingExposureRatherThanCorrelationAlone() {
        // maxExposure = 10_000 * 0.20 = 2000; existing open notional = 1900 -> only 100 left
        when(positionService.findAll()).thenReturn(List.of(openPosition("ETH-USD", 19, 100.0)));
        TradeDecision decision = approvedDecision(100.0, 10.0); // wants 1000 notional, only 100 available

        TradeDecision result = portfolioRiskService.apply(decision, "BTC-USD", 10_000, "corr-6", now);

        assertThat(result.approved()).isTrue();
        assertThat(result.positionSize()).isEqualTo(1.0); // 100 available / 100 entry price
    }
}
