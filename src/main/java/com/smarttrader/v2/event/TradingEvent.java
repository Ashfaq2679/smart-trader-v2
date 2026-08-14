package com.smarttrader.v2.event;

import java.util.UUID;

/**
 * Base class for all v2.5 domain events, per V2_TECH_SPEC_v2.5.md section 9:
 * "New events ... all idempotent, timestamped, correlated, schema-versioned."
 *
 * eventId/timestampNs/schemaVersion are set automatically on construction so every
 * concrete event subclass gets them "for free" (idempotency dedupe key, ordering,
 * and replay compatibility respectively). eventType/symbol/correlationId are set by
 * the subclass or the code that raises the event.
 *
 * Fields are public (not Lombok-wrapped) to match how later phases of
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md construct events (e.g.
 * {@code event.symbol = symbol;} from a different package).
 */
public abstract class TradingEvent {

    /** UUID; unique per occurrence, used by consumers to dedupe (idempotency). */
    public final String eventId = UUID.randomUUID().toString();

    /** UTC nanoseconds at construction time. */
    public final long timestampNs = System.nanoTime();

    /** e.g. "liquidity.SweepDetected"; set by the concrete subclass constructor. */
    public String eventType;

    /** Ties this event back to the business transaction/decision that produced it. */
    public String correlationId;

    public String symbol;

    /** For replay compatibility as the event shape evolves; starts at 1. */
    public int schemaVersion = 1;

    protected TradingEvent(String eventType) {
        this.eventType = eventType;
    }
}
