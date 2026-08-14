package com.smarttrader.v2.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 0.7 gate: "MarketRegime enum compiles with new entries". */
class MarketRegimeTest {

    @Test
    void v25RegimesArePresentAlongsideV22Regimes() {
        assertThat(MarketRegime.values()).containsExactlyInAnyOrder(
                MarketRegime.BREAKOUT, MarketRegime.CONTINUATION, MarketRegime.PULLBACK,
                MarketRegime.PANIC, MarketRegime.DISTRIBUTION,
                MarketRegime.RANGE, MarketRegime.CHOP, MarketRegime.NEWS_SHOCK,
                MarketRegime.SQUEEZE_LONG, MarketRegime.SQUEEZE_SHORT);
    }
}
