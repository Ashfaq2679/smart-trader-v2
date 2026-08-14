package com.smarttrader.v2.strategy;

import org.junit.jupiter.api.Test;

import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.MarketRegime;

import static org.assertj.core.api.Assertions.assertThat;

class AntiSMCStrategyTest {

    private final AntiSMCStrategy strategy = new AntiSMCStrategy();

    @Test
    void edgeCase_alwaysInvalidRegardlessOfContext() {
        AnalysisContext ctx = AnalysisContext.builder().price(100.0).atr(2.0).build();

        assertThat(strategy.evaluate(ctx).valid()).isFalse();
    }

    @Test
    void edgeCase_declaresItsApplicableRegimes() {
        assertThat(strategy.applicableRegimes()).containsExactlyInAnyOrder(MarketRegime.BREAKOUT, MarketRegime.CONTINUATION);
    }
}
