package com.smarttrader.v2.event;

import com.smarttrader.v2.model.MarketRegime;

import java.time.Instant;

public record RegimeDetectedEvent(
        String eventId,
        String correlationId,
        Instant timestamp,
        String productId,
        MarketRegime regime,
        double confidence
) implements DomainEvent {

    public static RegimeDetectedEvent of(String correlationId, String productId, MarketRegime regime, double confidence) {
        return new RegimeDetectedEvent(
                DeterministicId.from("RegimeDetected", correlationId, productId),
                correlationId,
                Instant.now(),
                productId,
                regime,
                confidence);
    }
}
