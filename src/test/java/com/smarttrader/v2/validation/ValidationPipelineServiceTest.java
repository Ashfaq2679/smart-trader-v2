package com.smarttrader.v2.validation;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.model.StrategyStage;
import com.smarttrader.v2.model.TradeOutcome;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationPipelineServiceTest {

    @Mock
    private BacktestRunner backtestRunner;
    @Mock
    private StrategyStateManager stateManager;
    @Mock
    private ShadowModeService shadowMode;
    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;

    private ValidationPipelineService service() {
        return new ValidationPipelineService(backtestRunner, stateManager, shadowMode, tradeOutcomeRepository);
    }

    @Test
    void bullish_researchPromotesToShadowWhenBacktestPassesAllGates() {
        when(backtestRunner.run("PullbackStrategy", "BTC-USD"))
                .thenReturn(new BacktestResult(600, 0.5, 0.2, 0.005));

        service().validateResearch("PullbackStrategy", "BTC-USD");

        verify(stateManager).promote("PullbackStrategy", "BTC-USD", StrategyStage.SHADOW, "backtest passed");
    }

    @Test
    void bearish_researchNeverPromotesOnNoBacktestData() {
        when(backtestRunner.run("PullbackStrategy", "BTC-USD")).thenReturn(BacktestResult.noData());

        service().validateResearch("PullbackStrategy", "BTC-USD");

        verify(stateManager, never()).promote(anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void bullish_shadowPromotesToMicroLiveWhenAllGatesPass() {
        when(shadowMode.getMetrics("PullbackStrategy", "BTC-USD"))
                .thenReturn(new ShadowMetrics(Duration.ofDays(30), 120, 0.9));

        service().validateShadow("PullbackStrategy", "BTC-USD");

        verify(stateManager).promote("PullbackStrategy", "BTC-USD", StrategyStage.MICRO_LIVE, "shadow passed");
    }

    @Test
    void edgeCase_shadowNeverPromotesWhenDistributionMatchFailsSafeAtZero() {
        when(shadowMode.getMetrics("PullbackStrategy", "BTC-USD"))
                .thenReturn(new ShadowMetrics(Duration.ofDays(30), 120, 0.0));

        service().validateShadow("PullbackStrategy", "BTC-USD");

        verify(stateManager, never()).promote(anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void bullish_microLivePromotesToFullWhenOutcomesSupportIt() {
        List<TradeOutcome> outcomes = List.of(
                TradeOutcome.builder().realizedR(1.0).slippageMultiple(1.0).build(),
                TradeOutcome.builder().realizedR(2.0).slippageMultiple(1.1).build());
        when(tradeOutcomeRepository.findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc("PullbackStrategy", "BTC-USD"))
                .thenReturn(outcomes);
        List<TradeOutcome> hundred = java.util.Collections.nCopies(100, outcomes.get(0));
        when(tradeOutcomeRepository.findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc("PullbackStrategy", "BTC-USD"))
                .thenReturn(hundred);

        service().validateMicroLive("PullbackStrategy", "BTC-USD");

        verify(stateManager).promote("PullbackStrategy", "BTC-USD", StrategyStage.FULL, "micro-live passed");
    }

    @Test
    void edgeCase_microLiveNeverPromotesWithNoRecordedOutcomes() {
        when(tradeOutcomeRepository.findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc("PullbackStrategy", "BTC-USD"))
                .thenReturn(List.of());

        service().validateMicroLive("PullbackStrategy", "BTC-USD");

        verify(stateManager, never()).promote(anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }
}
