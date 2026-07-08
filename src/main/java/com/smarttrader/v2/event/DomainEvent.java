package com.smarttrader.v2.event;

import java.time.Instant;

/**
 * Event Model, per V2_TECH_SPEC_v1.1.md section 9 (MANDATORY). Every event:
 * - is idempotent: eventId is deterministically derived from stable business keys
 *   (see DeterministicId), so republishing the same occurrence yields the same eventId
 *   and consumers can dedupe on it.
 * - includes a timestamp.
 * - includes a correlationId tying it back to the business transaction that produced it
 *   (e.g. the same correlationId threads through RegimeDetected -> SignalGenerated ->
 *   OrderPlaced -> PositionOpened for one trade decision).
 *
 * Published today via DomainEventPublisher (an in-process Spring ApplicationEventPublisher
 * wrapper); this is the seam where the Kafka topics from CLAUDE.md's roadmap (trend.score,
 * breakout.events, trade.decisions, etc.) would plug in later without changing producers.
 */
public sealed interface DomainEvent
        permits CandleUpdatedEvent, RegimeDetectedEvent, SignalGeneratedEvent, OrderPlacedEvent,
        OrderFilledEvent, PositionOpenedEvent, PositionClosedEvent, PortfolioUpdatedEvent {

    String eventId();

    String correlationId();

    Instant timestamp();
}
