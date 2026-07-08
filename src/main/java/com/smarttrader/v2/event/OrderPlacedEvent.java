package com.smarttrader.v2.event;

import com.smarttrader.v2.execution.OrderResult;

import java.time.Instant;

public record OrderPlacedEvent(
        String eventId,
        String correlationId,
        Instant timestamp,
        OrderResult orderResult
) implements DomainEvent {

    /** Fires for every place() outcome (PLACED/REJECTED/CANCELLED/EXPIRED), keyed by idempotencyKey. */
    public static OrderPlacedEvent of(String correlationId, OrderResult orderResult) {
        return new OrderPlacedEvent(
                DeterministicId.from("OrderPlaced", orderResult.idempotencyKey()),
                correlationId,
                Instant.now(),
                orderResult);
    }
}
