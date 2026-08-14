package com.smarttrader.v2.feedback;

import java.util.Comparator;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smarttrader.v2.constants.FeedbackConstants;
import com.smarttrader.v2.model.StrategyRiskBudget;
import com.smarttrader.v2.model.StrategyStage;
import com.smarttrader.v2.model.StrategyState;
import com.smarttrader.v2.model.TradeOutcome;
import com.smarttrader.v2.validation.StrategyStateRepository;
import com.smarttrader.v2.validation.TradeOutcomeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Weekly risk-budget reallocation across FULL-stage strategies, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5.3.
 *
 * Persists StrategyRiskBudget for audit/inspection but does not wire the multiplier into
 * RiskEngine/PositionSizing: RiskEngine is a pinned file (RiskEngineTest asserts its
 * exact current constructor/behavior), the same reason TradeEngine wasn't extended for
 * stage-checking in Phase 4 and Opportunity Siren wasn't wired into TradeEngine in Phase
 * 3. Once RiskEngine's pinned baseline is allowed to change, it can read this budget by
 * (strategyName, symbol) to scale position size.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetaAllocator {

    private final StrategyStateRepository stateRepository;
    private final TradeOutcomeRepository tradeOutcomeRepository;
    private final StrategyRiskBudgetRepository riskBudgetRepository;

    @Async
    @Scheduled(cron = "0 0 0 * * MON")
    public void reallocateRiskBudget() {
        List<StrategyState> fullStages = stateRepository.findAllByStage(StrategyStage.FULL);
        if (fullStages.isEmpty()) {
            return;
        }

        List<Ranked> ranked = fullStages.stream()
                .map(state -> new Ranked(state, rollingExpectancy(state)))
                .sorted(Comparator.comparingDouble(Ranked::expectancy).reversed())
                .toList();

        int size = ranked.size();
        for (int i = 0; i < size; i++) {
            double multiplier = tierMultiplier(i, size);
            persistBudget(ranked.get(i), multiplier);
        }
    }

    /**
     * Ceiling-divided tier boundaries so a small cohort (e.g. a single FULL-stage
     * strategy) still lands in the top tier instead of always falling to the bottom
     * third under integer-floor division.
     */
    private double tierMultiplier(int index, int size) {
        int topBoundary = (size + 2) / 3;
        int midBoundary = (2 * size + 2) / 3;
        if (index < topBoundary) {
            return FeedbackConstants.META_ALLOCATOR_TOP_TIER_MULTIPLIER;
        }
        if (index < midBoundary) {
            return FeedbackConstants.META_ALLOCATOR_MID_TIER_MULTIPLIER;
        }
        return FeedbackConstants.META_ALLOCATOR_BOTTOM_TIER_MULTIPLIER;
    }

    private double rollingExpectancy(StrategyState state) {
        List<TradeOutcome> recent = tradeOutcomeRepository.findTop60ByStrategyNameAndSymbolOrderByClosedAtDesc(
                state.getStrategyName(), state.getSymbol());
        return recent.stream().mapToDouble(TradeOutcome::getRealizedR).average().orElse(0.0);
    }

    private void persistBudget(Ranked ranked, double multiplier) {
        StrategyState state = ranked.state();
        StrategyRiskBudget budget = riskBudgetRepository.findByStrategyNameAndSymbol(state.getStrategyName(), state.getSymbol());
        if (budget == null) {
            budget = StrategyRiskBudget.builder()
                    .strategyName(state.getStrategyName())
                    .symbol(state.getSymbol())
                    .build();
        }
        budget.setMultiplier(multiplier);
        budget.setRollingExpectancy(ranked.expectancy());
        budget.setUpdatedAtNs(System.nanoTime());
        riskBudgetRepository.save(budget);

        log.info("metaAllocator strategy={} symbol={} expectancy={} multiplier={}",
                state.getStrategyName(), state.getSymbol(), ranked.expectancy(), multiplier);
    }

    private record Ranked(StrategyState state, double expectancy) {
    }
}
