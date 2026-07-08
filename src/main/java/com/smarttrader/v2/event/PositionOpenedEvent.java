package com.smarttrader.v2.event;

import com.smarttrader.v2.position.Position;

import java.time.Instant;

public record PositionOpenedEvent(
        String eventId,
        String correlationId,
        Instant timestamp,
        Position position
) implements DomainEvent {

    public static PositionOpenedEvent of(String correlationId, Position position) {
        return new PositionOpenedEvent(
                DeterministicId.from("PositionOpened", position.positionId()),
                correlationId,
                Instant.now(),
                position);
    }
}
