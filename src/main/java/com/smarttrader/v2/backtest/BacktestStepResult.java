package com.smarttrader.v2.backtest;

import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.position.Position;
import lombok.Builder;

import java.util.Optional;

/**
 * Outcome of replaying one BacktestTick through TradeEngine (and, if approved, through
 * PositionService/SimulatedFillEngine).
 */
@Builder
public record BacktestStepResult(
        BacktestTick tick,
        TradeDecision decision,
        Optional<Position> position
) {
}
