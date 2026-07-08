package com.smarttrader.v2.event;

import java.time.Instant;

public record PortfolioUpdatedEvent(
        String eventId,
        String correlationId,
        Instant timestamp,
        double totalExposure,
        int openPositionCount
) implements DomainEvent {

    public static PortfolioUpdatedEvent of(String correlationId, double totalExposure, int openPositionCount, Instant now) {
        return new PortfolioUpdatedEvent(
                DeterministicId.from("PortfolioUpdated", correlationId, now),
                correlationId,
                now,
                totalExposure,
                openPositionCount);
    }
}
