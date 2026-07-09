package com.smarttrader.v2.backtest;

import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedFillEngineTest {

    private final SimulatedFillEngine engine = new SimulatedFillEngine();

    private SignalResult signal(TradeDirection direction, EntryType entryType, double entry) {
        return SignalResult.builder().valid(true).strategyName("Test").direction(direction)
                .entry(entry).entryType(entryType).validityWindow(Duration.ofMinutes(5))
                .stop(entry - 5).target(entry + 10).riskReward(2.0).build();
    }

    @Test
    void bullish_marketOrderFillsImmediatelyAtCurrentPrice() {
        var result = engine.simulateFill(signal(TradeDirection.LONG, EntryType.MARKET, 100.0), 101.5);

        assertThat(result).contains(101.5);
    }

    @Test
    void bullish_longLimitOrderFillsOncePriceDropsToOrBelowEntry() {
        var result = engine.simulateFill(signal(TradeDirection.LONG, EntryType.LIMIT, 100.0), 99.5);

        assertThat(result).contains(100.0);
    }

    @Test
    void bearish_longLimitOrderDoesNotFillWhilePriceStaysAboveEntry() {
        var result = engine.simulateFill(signal(TradeDirection.LONG, EntryType.LIMIT, 100.0), 101.0);

        assertThat(result).isEmpty();
    }

    @Test
    void edgeCase_shortLimitOrderFillsOncePriceRisesToOrAboveEntry() {
        var result = engine.simulateFill(signal(TradeDirection.SHORT, EntryType.LIMIT, 100.0), 100.0);

        assertThat(result).contains(100.0);
    }
}
