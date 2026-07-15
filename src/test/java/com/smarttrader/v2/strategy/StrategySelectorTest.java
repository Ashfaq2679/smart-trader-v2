package com.smarttrader.v2.strategy;

import java.util.List;

import com.smarttrader.v2.model.MarketRegime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrategySelectorTest {

    private final PullbackStrategy pullbackStrategy = new PullbackStrategy();
    private final BreakoutStrategy breakoutStrategy = new BreakoutStrategy();
    private final ContinuationStrategy continuationStrategy = new ContinuationStrategy();
    private final SweepReclaimStrategy sweepReclaimStrategy = new SweepReclaimStrategy();
    private final SFPReversalStrategy sfpReversalStrategy = new SFPReversalStrategy();
    private final RangeHarvesterStrategy rangeHarvesterStrategy = new RangeHarvesterStrategy(false);
    private final CascadeReversalStrategy cascadeReversalStrategy = new CascadeReversalStrategy();

    private final StrategySelector selector =
            new StrategySelector(pullbackStrategy, breakoutStrategy, continuationStrategy,
                    sweepReclaimStrategy, sfpReversalStrategy, rangeHarvesterStrategy, cascadeReversalStrategy);

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

    // --- selectStrategies() / Playbook Matrix (Phase 2.6) ---

    @Test
    void bullish_breakoutPlaybookPairsTheV22StrategyWithSweepReclaim() {
        assertThat(selector.selectStrategies(MarketRegime.BREAKOUT))
                .containsExactly(breakoutStrategy, sweepReclaimStrategy);
    }

    @Test
    void bullish_rangePlaybookPairsRangeHarvesterWithSfpReversal() {
        assertThat(selector.selectStrategies(MarketRegime.RANGE))
                .containsExactly(rangeHarvesterStrategy, sfpReversalStrategy);
    }

    @Test
    void bearish_newsShockPlaybookOffersCascadeReversal() {
        assertThat(selector.selectStrategies(MarketRegime.NEWS_SHOCK))
                .containsExactly(cascadeReversalStrategy);
    }

    @Test
    void sideways_chopPlaybookIsEmptyNoTrade() {
        assertThat(selector.selectStrategies(MarketRegime.CHOP)).isEmpty();
    }

    @Test
    void edgeCase_unmappedRegimeReturnsEmptyPlaybookRatherThanThrowing() {
        assertThat(selector.selectStrategies(MarketRegime.DISTRIBUTION)).isEqualTo(List.of());
    }
}
