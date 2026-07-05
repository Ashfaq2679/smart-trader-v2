package com.smarttrader.v2.strategy;

import com.smarttrader.v2.model.MarketRegime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrategySelectorTest {

    private final PullbackStrategy pullbackStrategy = new PullbackStrategy();
    private final BreakoutStrategy breakoutStrategy = new BreakoutStrategy();
    private final ContinuationStrategy continuationStrategy = new ContinuationStrategy();

    private final StrategySelector selector =
            new StrategySelector(pullbackStrategy, breakoutStrategy, continuationStrategy);

    @Test
    void selectsPullbackStrategyForPullbackRegime() {
        assertThat(selector.select(MarketRegime.PULLBACK)).contains(pullbackStrategy);
    }

    @Test
    void selectsBreakoutStrategyForBreakoutRegime() {
        assertThat(selector.select(MarketRegime.BREAKOUT)).contains(breakoutStrategy);
    }

    @Test
    void selectsContinuationStrategyForContinuationRegime() {
        assertThat(selector.select(MarketRegime.CONTINUATION)).contains(continuationStrategy);
    }

    @Test
    void panicRegimeHasNoStrategy() {
        assertThat(selector.select(MarketRegime.PANIC)).isEmpty();
    }

    @Test
    void distributionRegimeHasNoStrategy() {
        assertThat(selector.select(MarketRegime.DISTRIBUTION)).isEmpty();
    }
}
