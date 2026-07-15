package com.smarttrader.v2.validation;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.event.StrategyStageChangedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.StrategyStage;
import com.smarttrader.v2.model.StrategyState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns validation-pipeline stage transitions for (strategy, symbol) pairs, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.2.
 *
 * Unlike the plan's code sample, promote()/demote() tolerate a missing StrategyState
 * (first-ever transition for a pair) by materializing one at RESEARCH rather than
 * NPE-ing on state.stage(), and both validate direction using StrategyStage's ordinal
 * order (RESEARCH < SHADOW < MICRO_LIVE < FULL) so a caller can't demote by naming a
 * higher stage or promote by naming a lower/equal one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyStateManager {

    private final StrategyStateRepository repository;
    private final TradingEventPublisher eventPublisher;

    public StrategyStage getStage(String strategyName, String symbol) {
        StrategyState state = repository.findByStrategyNameAndSymbol(strategyName, symbol);
        return state != null ? state.getStage() : StrategyStage.RESEARCH;
    }

    public void promote(String strategyName, String symbol, StrategyStage newStage, String reason) {
        StrategyState state = findOrCreate(strategyName, symbol);
        StrategyStage oldStage = state.getStage();
        if (newStage.ordinal() <= oldStage.ordinal()) {
            throw new IllegalArgumentException(
                    "promote() requires a stage strictly higher than current=" + oldStage + ", got " + newStage);
        }
        applyTransition(state, oldStage, newStage, reason);
    }

    public void demote(String strategyName, String symbol, StrategyStage newStage, String reason) {
        StrategyState state = findOrCreate(strategyName, symbol);
        StrategyStage oldStage = state.getStage();
        if (newStage.ordinal() >= oldStage.ordinal()) {
            throw new IllegalArgumentException(
                    "demote() requires a stage strictly lower than current=" + oldStage + ", got " + newStage);
        }
        applyTransition(state, oldStage, newStage, reason);
    }

    private StrategyState findOrCreate(String strategyName, String symbol) {
        StrategyState state = repository.findByStrategyNameAndSymbol(strategyName, symbol);
        if (state != null) {
            return state;
        }
        return StrategyState.builder()
                .strategyName(strategyName)
                .symbol(symbol)
                .stage(StrategyStage.RESEARCH)
                .build();
    }

    private void applyTransition(StrategyState state, StrategyStage oldStage, StrategyStage newStage, String reason) {
        state.setStage(newStage);
        state.setLastPromotedNs(System.nanoTime());
        state.setLastReason(reason);
        repository.save(state);

        StrategyStageChangedEvent event = new StrategyStageChangedEvent();
        event.symbol = state.getSymbol();
        event.strategyName = state.getStrategyName();
        event.oldStage = oldStage;
        event.newStage = newStage;
        event.reason = reason;
        eventPublisher.publish(event);

        log.info("strategyStage strategy={} symbol={} oldStage={} newStage={} reason={}",
                state.getStrategyName(), state.getSymbol(), oldStage, newStage, reason);
    }
}
