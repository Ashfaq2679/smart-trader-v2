package com.smarttrader.v2.strategy;

import com.smarttrader.v2.model.MarketRegime;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps a detected MarketRegime to the TradingStrategy that handles it.
 * PANIC and DISTRIBUTION have no corresponding strategy: per spec, no trade
 * is taken in those regimes.
 */
@Component
public class StrategySelector {

    private final Map<MarketRegime, TradingStrategy> strategiesByRegime;

    public StrategySelector(PullbackStrategy pullbackStrategy,
                             BreakoutStrategy breakoutStrategy,
                             ContinuationStrategy continuationStrategy) {
        Map<MarketRegime, TradingStrategy> map = new EnumMap<>(MarketRegime.class);
        map.put(MarketRegime.PULLBACK, pullbackStrategy);
        map.put(MarketRegime.BREAKOUT, breakoutStrategy);
        map.put(MarketRegime.CONTINUATION, continuationStrategy);
        this.strategiesByRegime = Map.copyOf(map);
    }

    public Optional<TradingStrategy> select(MarketRegime regime) {
        return Optional.ofNullable(strategiesByRegime.get(regime));
    }
}
