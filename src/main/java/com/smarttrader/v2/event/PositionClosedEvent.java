package com.smarttrader.v2.event;

import com.smarttrader.v2.position.Position;

import java.time.Instant;

public record PositionClosedEvent(
        String eventId,
        String correlationId,
        Instant timestamp,
        Position position
) implements DomainEvent {

    /** positionId + closedAt makes this idempotent even if the same close is retried. */
    public static PositionClosedEvent of(String correlationId, Position position) {
        return new PositionClosedEvent(
                DeterministicId.from("PositionClosed", position.positionId(), position.closedAt()),
                correlationId,
                Instant.now(),
                position);
    }
}
