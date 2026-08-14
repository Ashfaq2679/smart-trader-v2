package com.smarttrader.v2.strategy;

import com.smarttrader.v2.model.MarketRegime;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps a detected MarketRegime to the TradingStrategy that handles it.
 * PANIC and DISTRIBUTION have no corresponding strategy: per spec, no trade
 * is taken in those regimes.
 *
 * selectStrategies(), per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 2.6, is additive:
 * it returns the primary + secondary strategy pair from the section 2 Playbook Matrix
 * without touching select()/the v2.2 single-strategy path TradeEngine still uses.
 * Wiring TradeEngine to actually consume selectStrategies() (with stage-gating) is
 * Phase 4's job, not this one's.
 *
 * SQUEEZE_LONG/SQUEEZE_SHORT's primary play in the spec is a defensive flatten
 * ("prepare shorts/longs", not a strategy that produces its own SignalResult) - not
 * implemented here or in any Phase 2 strategy, so only the secondary play is listed for
 * those two regimes. AntiSMCStrategy and ShortSideStrategy aren't wired into the matrix
 * itself (the spec's table doesn't call them out as a primary/secondary pick for any
 * single regime); they remain registered strategies with their own applicableRegimes()
 * for future, more flexible selection logic to use.
 */
@Component
public class StrategySelector {

    private final Map<MarketRegime, TradingStrategy> strategiesByRegime;
    private final Map<MarketRegime, List<TradingStrategy>> playbookByRegime;

    public StrategySelector(PullbackStrategy pullbackStrategy,
                             BreakoutStrategy breakoutStrategy,
                             ContinuationStrategy continuationStrategy,
                             SweepReclaimStrategy sweepReclaimStrategy,
                             SFPReversalStrategy sfpReversalStrategy,
                             RangeHarvesterStrategy rangeHarvesterStrategy,
                             CascadeReversalStrategy cascadeReversalStrategy) {
        Map<MarketRegime, TradingStrategy> map = new EnumMap<>(MarketRegime.class);
        map.put(MarketRegime.PULLBACK, pullbackStrategy);
        map.put(MarketRegime.BREAKOUT, breakoutStrategy);
        map.put(MarketRegime.CONTINUATION, continuationStrategy);
        this.strategiesByRegime = Map.copyOf(map);

        Map<MarketRegime, List<TradingStrategy>> playbook = new EnumMap<>(MarketRegime.class);
        playbook.put(MarketRegime.BREAKOUT, List.of(breakoutStrategy, sweepReclaimStrategy));
        playbook.put(MarketRegime.CONTINUATION, List.of(continuationStrategy, sfpReversalStrategy));
        playbook.put(MarketRegime.PULLBACK, List.of(pullbackStrategy, rangeHarvesterStrategy));
        playbook.put(MarketRegime.RANGE, List.of(rangeHarvesterStrategy, sfpReversalStrategy));
        playbook.put(MarketRegime.SQUEEZE_LONG, List.of(sweepReclaimStrategy));
        playbook.put(MarketRegime.SQUEEZE_SHORT, List.of(rangeHarvesterStrategy));
        playbook.put(MarketRegime.CHOP, List.of());
        playbook.put(MarketRegime.NEWS_SHOCK, List.of(cascadeReversalStrategy));
        playbook.put(MarketRegime.PANIC, List.of(pullbackStrategy, sfpReversalStrategy));
        this.playbookByRegime = Map.copyOf(playbook);
    }

    public Optional<TradingStrategy> select(MarketRegime regime) {
        return Optional.ofNullable(strategiesByRegime.get(regime));
    }

    /** Primary + secondary strategy pair for `regime`, per section 2's Playbook Matrix. */
    public List<TradingStrategy> selectStrategies(MarketRegime regime) {
        return playbookByRegime.getOrDefault(regime, List.of());
    }
}
