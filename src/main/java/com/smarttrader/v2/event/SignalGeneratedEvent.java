package com.smarttrader.v2.event;

import com.smarttrader.v2.model.SignalResult;

import java.time.Instant;

public record SignalGeneratedEvent(
        String eventId,
        String correlationId,
        Instant timestamp,
        String productId,
        SignalResult signal
) implements DomainEvent {

    public static SignalGeneratedEvent of(String correlationId, String productId, SignalResult signal) {
        return new SignalGeneratedEvent(
                DeterministicId.from("SignalGenerated", correlationId, productId),
                correlationId,
                Instant.now(),
                productId,
                signal);
    }
}
