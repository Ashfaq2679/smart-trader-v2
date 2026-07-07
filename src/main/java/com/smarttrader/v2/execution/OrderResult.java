package com.smarttrader.v2.execution;

import com.smarttrader.v2.model.TradeDirection;
import lombok.Builder;

import java.time.Instant;

/**
 * Result of OrderExecutionService.place(), per V2_TECH_SPEC_v1.1.md section 6.
 */
@Builder
public record OrderResult(
        String idempotencyKey,
        String productId,
        OrderStatus status,
        String reason,
        TradeDirection direction,
        double requestedPrice,
        double quotedPrice,
        double slippage,
        double positionSize,
        Instant evaluatedAt
) {
}
