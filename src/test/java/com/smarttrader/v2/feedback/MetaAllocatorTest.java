package com.smarttrader.v2.feedback;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.model.StrategyRiskBudget;
import com.smarttrader.v2.model.StrategyStage;
import com.smarttrader.v2.model.StrategyState;
import com.smarttrader.v2.model.TradeOutcome;
import com.smarttrader.v2.validation.StrategyStateRepository;
import com.smarttrader.v2.validation.TradeOutcomeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaAllocatorTest {

    @Mock
    private StrategyStateRepository stateRepository;
    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;
    @Mock
    private StrategyRiskBudgetRepository riskBudgetRepository;

    private MetaAllocator allocator() {
        return new MetaAllocator(stateRepository, tradeOutcomeRepository, riskBudgetRepository);
    }

    private StrategyState state(String name) {
        return StrategyState.builder().strategyName(name).symbol("BTC-USD").stage(StrategyStage.FULL).build();
    }

    private List<TradeOutcome> outcomesWithExpectancy(double realizedR) {
        return List.of(TradeOutcome.builder().realizedR(realizedR).build());
    }

    @Test
    void bullish_topThirdRankedStrategyGetsBoostedMultiplier() {
        StrategyState best = state("BreakoutStrategy");
        StrategyState mid = state("PullbackStrategy");
        StrategyState worst = state("RangeHarvesterStrategy");
        when(stateRepository.findAllByStage(StrategyStage.FULL)).thenReturn(List.of(best, mid, worst));
        when(tradeOutcomeRepository.findTop60ByStrategyNameAndSymbolOrderByClosedAtDesc("BreakoutStrategy", "BTC-USD"))
                .thenReturn(outcomesWithExpectancy(3.0));
        when(tradeOutcomeRepository.findTop60ByStrategyNameAndSymbolOrderByClosedAtDesc("PullbackStrategy", "BTC-USD"))
                .thenReturn(outcomesWithExpectancy(1.0));
        when(tradeOutcomeRepository.findTop60ByStrategyNameAndSymbolOrderByClosedAtDesc("RangeHarvesterStrategy", "BTC-USD"))
                .thenReturn(outcomesWithExpectancy(-1.0));
        when(riskBudgetRepository.findByStrategyNameAndSymbol(any(), any())).thenReturn(null);

        allocator().reallocateRiskBudget();

        ArgumentCaptor<StrategyRiskBudget> captor = ArgumentCaptor.forClass(StrategyRiskBudget.class);
        verify(riskBudgetRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        List<StrategyRiskBudget> saved = captor.getAllValues();

        assertThat(saved.get(0).getStrategyName()).isEqualTo("BreakoutStrategy");
        assertThat(saved.get(0).getMultiplier()).isEqualTo(1.2);
        assertThat(saved.get(2).getStrategyName()).isEqualTo("RangeHarvesterStrategy");
        assertThat(saved.get(2).getMultiplier()).isEqualTo(0.6);
    }

    @Test
    void bearish_noFullStageStrategiesIsANoOp() {
        when(stateRepository.findAllByStage(StrategyStage.FULL)).thenReturn(List.of());

        allocator().reallocateRiskBudget();

        verify(riskBudgetRepository, never()).save(any());
    }

    @Test
    void edgeCase_existingBudgetIsUpdatedInPlaceNotDuplicated() {
        StrategyState only = state("BreakoutStrategy");
        when(stateRepository.findAllByStage(StrategyStage.FULL)).thenReturn(List.of(only));
        when(tradeOutcomeRepository.findTop60ByStrategyNameAndSymbolOrderByClosedAtDesc("BreakoutStrategy", "BTC-USD"))
                .thenReturn(outcomesWithExpectancy(2.0));
        StrategyRiskBudget existing = StrategyRiskBudget.builder()
                .strategyName("BreakoutStrategy").symbol("BTC-USD").multiplier(0.6).build();
        when(riskBudgetRepository.findByStrategyNameAndSymbol("BreakoutStrategy", "BTC-USD")).thenReturn(existing);

        allocator().reallocateRiskBudget();

        assertThat(existing.getMultiplier()).isEqualTo(1.2);
        verify(riskBudgetRepository).save(existing);
    }
}
