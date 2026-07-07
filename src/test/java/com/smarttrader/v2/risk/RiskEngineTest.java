package com.smarttrader.v2.risk;

import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskEngineTest {

    @Mock
    private PositionSizing positionSizing;

    private RiskEngine riskEngine;

    @BeforeEach
    void setUp() {
        riskEngine = new RiskEngine(positionSizing);
    }

    private SignalResult signal(boolean valid, double entry, double stop, double target) {
        return SignalResult.builder()
                .valid(valid)
                .strategyName("TestStrategy")
                .direction(TradeDirection.LONG)
                .entry(entry)
                .stop(stop)
                .target(target)
                .build();
    }

    @Test
    void bullish_approvesTradeMeetingMinimumEffectiveRiskReward() {
        when(positionSizing.calculate(10_000, 0.01, 100.0, 95.0)).thenReturn(20.0);

        TradeDecision decision = riskEngine.evaluate(MarketRegime.PULLBACK, signal(true, 100.0, 95.0, 112.0), 10_000);

        assertThat(decision.approved()).isTrue();
        assertThat(decision.positionSize()).isEqualTo(20.0);
        assertThat(decision.effectiveRiskReward()).isEqualTo(12.0 / 5.0);
    }

    @Test
    void bearish_rejectsInvalidSignalFromStrategy() {
        TradeDecision decision = riskEngine.evaluate(MarketRegime.BREAKOUT, signal(false, 100.0, 95.0, 115.0), 10_000);

        assertThat(decision.approved()).isFalse();
    }

    @Test
    void sideways_rejectsWhenEffectiveRiskRewardBelowMinimum() {
        TradeDecision decision = riskEngine.evaluate(MarketRegime.CONTINUATION, signal(true, 100.0, 95.0, 104.0), 10_000);

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason()).contains("below minimum");
    }

    @Test
    void edgeCase_rejectsWhenEntryEqualsStopBecauseEffectiveRiskIsZero() {
        TradeDecision decision = riskEngine.evaluate(MarketRegime.PULLBACK, signal(true, 100.0, 100.0, 130.0), 10_000);

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason()).contains("below minimum");
    }

    @Test
    void edgeCase_rejectsWhenPositionSizingReturnsZeroDespiteGoodRiskReward() {
        when(positionSizing.calculate(10_000, 0.01, 100.0, 95.0)).thenReturn(0.0);

        TradeDecision decision = riskEngine.evaluate(MarketRegime.PULLBACK, signal(true, 100.0, 95.0, 115.0), 10_000);

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason()).contains("stop distance");
    }

    @Test
    void feesAndSlippageReduceEffectiveRiskRewardBelowMinimumEvenWhenRawRatioIsSufficient() {
        TradeDecision decision = riskEngine.evaluate(MarketRegime.BREAKOUT,
                signal(true, 100.0, 95.0, 112.0), 10_000, 0.01, 3.0, 2.0, 0.75);

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason()).contains("below minimum");
        assertThat(decision.regimeConfidence()).isEqualTo(0.75);
    }
}
