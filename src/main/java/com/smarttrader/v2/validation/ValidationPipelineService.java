package com.smarttrader.v2.validation;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.constants.ValidationConstants;
import com.smarttrader.v2.model.StrategyStage;
import com.smarttrader.v2.model.TradeOutcome;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gates promotion through the validation pipeline, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationPipelineService {

    private final BacktestRunner backtestRunner;
    private final StrategyStateManager stateManager;
    private final ShadowModeService shadowMode;
    private final TradeOutcomeRepository tradeOutcomeRepository;

    public void validateResearch(String strategyName, String symbol) {
        BacktestResult result = backtestRunner.run(strategyName, symbol);

        boolean passes = result.trades() >= ValidationConstants.RESEARCH_MIN_BACKTEST_TRADES
                && result.expectancy() > 0
                && result.paramSensitivity() > 0
                && result.monteCarloRoR() < ValidationConstants.RESEARCH_MAX_MONTE_CARLO_ROR;

        if (passes) {
            stateManager.promote(strategyName, symbol, StrategyStage.SHADOW, "backtest passed");
        } else {
            log.info("validateResearch strategy={} symbol={} passed=false trades={} expectancy={}",
                    strategyName, symbol, result.trades(), result.expectancy());
        }
    }

    public void validateShadow(String strategyName, String symbol) {
        ShadowMetrics metrics = shadowMode.getMetrics(strategyName, symbol);

        boolean passes = metrics.age().toDays() >= ValidationConstants.SHADOW_MIN_AGE_DAYS
                && metrics.signalCount() >= ValidationConstants.SHADOW_MIN_SIGNAL_COUNT
                && metrics.distributionMatch() >= ValidationConstants.SHADOW_MIN_DISTRIBUTION_MATCH;

        if (passes) {
            stateManager.promote(strategyName, symbol, StrategyStage.MICRO_LIVE, "shadow passed");
        } else {
            log.info("validateShadow strategy={} symbol={} passed=false ageDays={} signalCount={} distributionMatch={}",
                    strategyName, symbol, metrics.age().toDays(), metrics.signalCount(), metrics.distributionMatch());
        }
    }

    public void validateMicroLive(String strategyName, String symbol) {
        LiveMetrics metrics = getLiveMetrics(strategyName, symbol);

        boolean passes = metrics.fills() >= ValidationConstants.MICRO_LIVE_MIN_FILLS
                && metrics.slippage() <= ValidationConstants.MICRO_LIVE_MAX_SLIPPAGE_MULTIPLE
                && metrics.expectancy() > 0;

        if (passes) {
            stateManager.promote(strategyName, symbol, StrategyStage.FULL, "micro-live passed");
        } else {
            log.info("validateMicroLive strategy={} symbol={} passed=false fills={} slippage={} expectancy={}",
                    strategyName, symbol, metrics.fills(), metrics.slippage(), metrics.expectancy());
        }
    }

    /**
     * Real once TradeOutcome records exist for this pair; honestly empty (never "assume
     * fine") until then, per TradeOutcome's javadoc on this codebase's current lack of
     * fill persistence.
     */
    private LiveMetrics getLiveMetrics(String strategyName, String symbol) {
        List<TradeOutcome> outcomes = tradeOutcomeRepository.findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc(strategyName, symbol);
        if (outcomes.isEmpty()) {
            return LiveMetrics.empty();
        }

        double avgR = outcomes.stream().mapToDouble(TradeOutcome::getRealizedR).average().orElse(0.0);
        double avgSlippage = outcomes.stream().mapToDouble(TradeOutcome::getSlippageMultiple).average().orElse(Double.MAX_VALUE);
        return new LiveMetrics(outcomes.size(), avgSlippage, avgR);
    }
}
