package com.smarttrader.v2.position;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock
    private DomainEventPublisher eventPublisher;

    private PositionService positionService;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        positionService = new PositionService(eventPublisher);
    }

    private TradeDecision approvedDecision(TradeDirection direction, double entry, double stop, double target, double size) {
        SignalResult signal = SignalResult.builder()
                .valid(true)
                .strategyName("PullbackStrategy")
                .direction(direction)
                .entry(entry)
                .entryType(EntryType.LIMIT)
                .validityWindow(Duration.ofMinutes(15))
                .stop(stop)
                .target(target)
                .riskReward(2.0)
                .build();

        return TradeDecision.builder()
                .approved(true)
                .regime(MarketRegime.PULLBACK)
                .regimeConfidence(0.8)
                .signal(signal)
                .effectiveRiskReward(2.0)
                .positionSize(size)
                .reason("approved")
                .build();
    }

    // --- Bullish scenarios ---

    @Test
    void bullish_opensPendingPositionThenFullyFillsToOpen() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);

        Position opened = positionService.open(decision, "BTC-USD", "pos-1", now);
        assertThat(opened.status()).isEqualTo(PositionStatus.PENDING);

        Position filled = positionService.recordFill("pos-1", 10.0, now.plusSeconds(5));

        assertThat(filled.status()).isEqualTo(PositionStatus.OPEN);
        assertThat(filled.filledSize()).isEqualTo(10.0);
        assertThat(filled.remainingSize()).isEqualTo(0.0);
    }

    @Test
    void bullish_partialFillsAccumulateBeforeReachingOpen() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-2", now);

        Position afterFirstFill = positionService.recordFill("pos-2", 4.0, now);
        assertThat(afterFirstFill.status()).isEqualTo(PositionStatus.PARTIALLY_FILLED);
        assertThat(afterFirstFill.filledSize()).isEqualTo(4.0);

        Position afterSecondFill = positionService.recordFill("pos-2", 6.0, now);
        assertThat(afterSecondFill.status()).isEqualTo(PositionStatus.OPEN);
        assertThat(afterSecondFill.filledSize()).isEqualTo(10.0);
    }

    @Test
    void bullish_smallUnrealizedLossWellUnderThresholdStaysOpen() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-3", now);
        positionService.recordFill("pos-3", 10.0, now);

        Position result = positionService.evaluateUnrealizedLossGuard("pos-3", 98.0, now);

        assertThat(result.status()).isEqualTo(PositionStatus.OPEN);
    }

    // --- Bearish scenarios ---

    @Test
    void bearish_forceClosesLongPositionWhenLossExceeds1point5xRisk() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-4", now);
        positionService.recordFill("pos-4", 10.0, now);

        // risk/unit = 5, threshold = 7.5 loss/unit -> price must drop below 92.5
        Position result = positionService.evaluateUnrealizedLossGuard("pos-4", 92.0, now.plusSeconds(30));

        assertThat(result.status()).isEqualTo(PositionStatus.CLOSED);
        assertThat(result.closeReason()).contains("unrealized loss guard");
    }

    @Test
    void bearish_forceClosesShortPositionWhenLossExceeds1point5xRisk() {
        TradeDecision decision = approvedDecision(TradeDirection.SHORT, 100.0, 105.0, 90.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-5", now);
        positionService.recordFill("pos-5", 10.0, now);

        // risk/unit = 5, threshold = 7.5 loss/unit -> price must rise above 107.5
        Position result = positionService.evaluateUnrealizedLossGuard("pos-5", 108.0, now.plusSeconds(30));

        assertThat(result.status()).isEqualTo(PositionStatus.CLOSED);
    }

    // --- Sideways / edge cases ---

    @Test
    void edgeCase_lossGuardIsNoOpForPositionWithNoFillsYet() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-6", now);

        Position result = positionService.evaluateUnrealizedLossGuard("pos-6", 50.0, now);

        assertThat(result.status()).isEqualTo(PositionStatus.PENDING);
    }

    @Test
    void edgeCase_lossExactlyAtThresholdDoesNotForceClose() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-7", now);
        positionService.recordFill("pos-7", 10.0, now);

        // risk/unit = 5, threshold = 7.5 exactly -> price = 92.5
        Position result = positionService.evaluateUnrealizedLossGuard("pos-7", 92.5, now);

        assertThat(result.status()).isEqualTo(PositionStatus.OPEN);
    }

    @Test
    void edgeCase_fillExceedingRemainingSizeIsRejected() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-8", now);

        assertThatThrownBy(() -> positionService.recordFill("pos-8", 15.0, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void edgeCase_fillAfterCloseIsRejectedAsIllegalTransition() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-9", now);
        positionService.close("pos-9", "manual exit", now);

        assertThatThrownBy(() -> positionService.recordFill("pos-9", 5.0, now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void edgeCase_closingAlreadyClosedPositionIsRejected() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-10", now);
        positionService.close("pos-10", "target hit", now);

        assertThatThrownBy(() -> positionService.close("pos-10", "again", now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void edgeCase_duplicatePositionIdIsRejected() {
        TradeDecision decision = approvedDecision(TradeDirection.LONG, 100.0, 95.0, 110.0, 10.0);
        positionService.open(decision, "BTC-USD", "pos-11", now);

        assertThatThrownBy(() -> positionService.open(decision, "BTC-USD", "pos-11", now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void edgeCase_openingFromAnUnapprovedDecisionIsRejected() {
        TradeDecision rejected = TradeDecision.rejected(MarketRegime.BREAKOUT, SignalResult.invalid("BreakoutStrategy"), "below minimum R:R");

        assertThatThrownBy(() -> positionService.open(rejected, "BTC-USD", "pos-12", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void edgeCase_operatingOnUnknownPositionIdThrows() {
        assertThatThrownBy(() -> positionService.recordFill("missing", 1.0, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> positionService.close("missing", "n/a", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> positionService.evaluateUnrealizedLossGuard("missing", 100.0, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
