package com.smarttrader.v2.validation;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.model.StrategyStage;
import com.smarttrader.v2.model.TradeOutcome;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyDemotionMonitorTest {

    @Mock
    private StrategyStateManager stateManager;
    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;

    private StrategyDemotionMonitor monitor() {
        return new StrategyDemotionMonitor(stateManager, tradeOutcomeRepository);
    }

    private TradeOutcome outcome(double realizedR, double slippageMultiple) {
        return TradeOutcome.builder().realizedR(realizedR).slippageMultiple(slippageMultiple).build();
    }

    @Test
    void bearish_negativeRollingExpectancyDemotesOneLevel() {
        List<TradeOutcome> outcomes = Collections.nCopies(20, outcome(-1.0, 1.0));
        when(tradeOutcomeRepository.findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc("PullbackStrategy", "BTC-USD"))
                .thenReturn(outcomes);
        when(stateManager.getStage("PullbackStrategy", "BTC-USD")).thenReturn(StrategyStage.MICRO_LIVE);

        monitor().checkDemotionRules("PullbackStrategy", "BTC-USD");

        verify(stateManager).demote("PullbackStrategy", "BTC-USD", StrategyStage.SHADOW, "rolling expectancy negative");
    }

    @Test
    void bearish_excessiveSlippageDemotesToShadow() {
        List<TradeOutcome> outcomes = Collections.nCopies(20, outcome(1.0, 3.0));
        when(tradeOutcomeRepository.findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc("PullbackStrategy", "BTC-USD"))
                .thenReturn(outcomes);
        when(stateManager.getStage("PullbackStrategy", "BTC-USD")).thenReturn(StrategyStage.FULL);

        monitor().checkDemotionRules("PullbackStrategy", "BTC-USD");

        verify(stateManager).demote("PullbackStrategy", "BTC-USD", StrategyStage.SHADOW, "slippage > 2.0x");
    }

    @Test
    void bullish_healthyOutcomesNeverTriggerDemotion() {
        List<TradeOutcome> outcomes = Collections.nCopies(20, outcome(1.0, 1.0));
        when(tradeOutcomeRepository.findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc("PullbackStrategy", "BTC-USD"))
                .thenReturn(outcomes);

        monitor().checkDemotionRules("PullbackStrategy", "BTC-USD");

        verify(stateManager, never()).demote(any(), any(), any(), any());
    }

    @Test
    void edgeCase_insufficientTradeHistoryNeverTriggersDemotion() {
        List<TradeOutcome> outcomes = Collections.nCopies(5, outcome(-5.0, 5.0));
        when(tradeOutcomeRepository.findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc("PullbackStrategy", "BTC-USD"))
                .thenReturn(outcomes);

        monitor().checkDemotionRules("PullbackStrategy", "BTC-USD");

        verify(stateManager, never()).demote(any(), any(), any(), any());
    }

    @Test
    void edgeCase_expectancyDemotionNeverDropsBelowResearch() {
        List<TradeOutcome> outcomes = Collections.nCopies(20, outcome(-1.0, 1.0));
        when(tradeOutcomeRepository.findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc("PullbackStrategy", "BTC-USD"))
                .thenReturn(outcomes);
        when(stateManager.getStage("PullbackStrategy", "BTC-USD")).thenReturn(StrategyStage.RESEARCH);

        monitor().checkDemotionRules("PullbackStrategy", "BTC-USD");

        verify(stateManager, never()).demote(any(), any(), any(), any());
    }
}
