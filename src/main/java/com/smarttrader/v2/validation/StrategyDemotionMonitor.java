package com.smarttrader.v2.validation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.smarttrader.v2.constants.ValidationConstants;
import com.smarttrader.v2.model.StrategyStage;
import com.smarttrader.v2.model.TradeOutcome;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Continuous demotion monitoring, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.4
 * ("runs every candle close" - the scheduling trigger for that is left to the caller,
 * e.g. a future trading scheduler tick, since this class only owns the rule logic).
 *
 * Unlike the plan's code sample, this never demotes on fewer than
 * ValidationConstants.DEMOTION_LOOKBACK_TRADES outcomes: too little evidence should
 * leave a strategy where it is, not falsely trigger a demotion off statistical noise.
 * The plan's third rule (win rate vs. backtest win rate) is omitted here - it needs a
 * persisted backtest win rate, which doesn't exist without a real BacktestRunner (see
 * that interface's javadoc); wiring it is a follow-up once backtesting is real.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyDemotionMonitor {

    private final StrategyStateManager stateManager;
    private final TradeOutcomeRepository tradeOutcomeRepository;

    public void checkDemotionRules(String strategyName, String symbol) {
        List<TradeOutcome> recent = tradeOutcomeRepository
                .findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc(strategyName, symbol);

        if (recent.size() < ValidationConstants.DEMOTION_LOOKBACK_TRADES) {
            log.info("demotionCheck strategy={} symbol={} skipped reason=insufficient trade history ({} < {})",
                    strategyName, symbol, recent.size(), ValidationConstants.DEMOTION_LOOKBACK_TRADES);
            return;
        }

        double expectancy = recent.stream().mapToDouble(TradeOutcome::getRealizedR).average().orElse(0.0);
        if (expectancy < 0) {
            demoteOneLevel(strategyName, symbol, "rolling expectancy negative");
            return;
        }

        double avgSlippage = recent.stream().mapToDouble(TradeOutcome::getSlippageMultiple).average().orElse(0.0);
        if (avgSlippage > ValidationConstants.DEMOTION_SLIPPAGE_MULTIPLE) {
            demoteTo(strategyName, symbol, StrategyStage.SHADOW, "slippage > " + ValidationConstants.DEMOTION_SLIPPAGE_MULTIPLE + "x");
        }
    }

    private void demoteOneLevel(String strategyName, String symbol, String reason) {
        StrategyStage current = stateManager.getStage(strategyName, symbol);
        if (current == StrategyStage.RESEARCH) {
            return;
        }
        StrategyStage target = StrategyStage.values()[current.ordinal() - 1];
        demoteTo(strategyName, symbol, target, reason);
    }

    private void demoteTo(String strategyName, String symbol, StrategyStage target, String reason) {
        StrategyStage current = stateManager.getStage(strategyName, symbol);
        if (target.ordinal() >= current.ordinal()) {
            return;
        }
        stateManager.demote(strategyName, symbol, target, reason);
    }
}
