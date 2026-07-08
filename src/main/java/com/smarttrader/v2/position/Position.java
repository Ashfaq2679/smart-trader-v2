package com.smarttrader.v2.position;

import com.smarttrader.v2.model.TradeDirection;
import lombok.Builder;

import java.time.Instant;

/**
 * An open (or closed) trade, per V2_TECH_SPEC_v1.1.md section 7. Immutable: PositionService
 * produces a new Position for every state change rather than mutating one in place.
 */
@Builder(toBuilder = true)
public record Position(
        String positionId,
        String productId,
        TradeDirection direction,
        double entryPrice,
        double stopPrice,
        double targetPrice,
        double requestedSize,
        double filledSize,
        PositionStatus status,
        Instant openedAt,
        Instant closedAt,
        String closeReason
) {

    /** Portion of requestedSize not yet filled. */
    public double remainingSize() {
        return requestedSize - filledSize;
    }

    /** Original per-unit risk the position was sized against: |entry - stop|. */
    public double riskPerUnit() {
        return Math.abs(entryPrice - stopPrice);
    }

    /**
     * Unrealized loss per unit at the given price; positive means losing, negative means
     * in profit. Only meaningful once filledSize > 0.
     */
    public double unrealizedLossPerUnit(double currentPrice) {
        return direction == TradeDirection.LONG
                ? entryPrice - currentPrice
                : currentPrice - entryPrice;
    }
}
