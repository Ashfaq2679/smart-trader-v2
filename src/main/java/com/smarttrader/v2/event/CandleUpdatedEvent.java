package com.smarttrader.v2.event;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;

import java.time.Instant;

public record CandleUpdatedEvent(
        String eventId,
        String correlationId,
        Instant timestamp,
        String productId,
        Granularity granularity,
        Candle candle
) implements DomainEvent {

    /**
     * eventId/correlationId are both derived from (productId, granularity, candle timestamp):
     * re-ingesting the same candle always produces the same event, satisfying idempotency
     * even though candle ingestion isn't part of a single caller-supplied business transaction.
     */
    public static CandleUpdatedEvent of(String productId, Granularity granularity, Candle candle) {
        String correlationId = DeterministicId.from("candle", productId, granularity, candle.timestamp());
        return new CandleUpdatedEvent(
                DeterministicId.from("CandleUpdated", correlationId),
                correlationId,
                Instant.now(),
                productId,
                granularity,
                candle);
    }
}
