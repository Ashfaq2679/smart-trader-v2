package com.smarttrader.v2.event;

import java.time.Instant;

public record OrderFilledEvent(
        String eventId,
        String correlationId,
        Instant timestamp,
        String positionId,
        double fillQuantity,
        double filledSize
) implements DomainEvent {

    public static OrderFilledEvent of(String correlationId, String positionId, double fillQuantity,
                                       double filledSize, Instant now) {
        return new OrderFilledEvent(
                DeterministicId.from("OrderFilled", positionId, filledSize, now),
                correlationId,
                now,
                positionId,
                fillQuantity,
                filledSize);
    }
}
