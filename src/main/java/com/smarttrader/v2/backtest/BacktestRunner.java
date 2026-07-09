package com.smarttrader.v2.backtest;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.position.Position;
import com.smarttrader.v2.position.PositionService;
import com.smarttrader.v2.risk.RiskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Historical replay, per V2_TECH_SPEC_v1.1.md section 12:
 * - Deterministic mode: reuses the exact same TradeEngine/PositionService pipeline as
 *   live trading (Final Insight: "System must behave identically in live trading, replay
 *   mode, and backtesting") - this class adds no separate decision logic of its own.
 * - Historical replay: iterates a pre-built timeline of BacktestTicks.
 * - Simulated fills: via SimulatedFillEngine, never a real exchange call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BacktestRunner {

    private final com.smarttrader.v2.engine.TradeEngine tradeEngine;
    private final PositionService positionService;
    private final SimulatedFillEngine simulatedFillEngine;

    public List<BacktestStepResult> run(List<BacktestTick> timeline, double capital) {
        List<BacktestStepResult> results = new ArrayList<>(timeline.size());
        int step = 0;
        for (BacktestTick tick : timeline) {
            results.add(runStep(tick, capital, "backtest-" + step++));
        }
        log.info("backtestRunner completed steps={} approvedDecisions={}", results.size(),
                results.stream().filter(r -> r.decision().approved()).count());
        return results;
    }

    private BacktestStepResult runStep(BacktestTick tick, double capital, String correlationId) {
        TradeDecision decision = tradeEngine.decide(tick.context(), tick.productId(), capital,
                RiskEngine.DEFAULT_RISK_PERCENT, TradingConstants.DEFAULT_FEES, TradingConstants.DEFAULT_SLIPPAGE,
                correlationId);

        Optional<Position> position = Optional.empty();
        if (decision.approved()) {
            String positionId = correlationId + "-position";
            Position opened = positionService.open(decision, tick.productId(), positionId, tick.timestamp(), correlationId);

            Optional<Double> fillPrice = simulatedFillEngine.simulateFill(decision.signal(), tick.price());
            position = Optional.of(fillPrice.isPresent()
                    ? positionService.recordFill(positionId, decision.positionSize(), tick.timestamp(), correlationId)
                    : opened);
        }

        return BacktestStepResult.builder().tick(tick).decision(decision).position(position).build();
    }
}
