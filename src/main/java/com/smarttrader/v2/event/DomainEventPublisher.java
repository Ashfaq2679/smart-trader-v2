package com.smarttrader.v2.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around Spring's in-process ApplicationEventPublisher. This is the single
 * seam for publishing DomainEvents; per CLAUDE.md's roadmap (Kafka Streams, candles.*
 * topics, trend.score/breakout.events/trade.decisions), a Kafka-backed implementation can
 * replace/augment this later without changing any producer call site.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(DomainEvent event) {
        log.info("event type={} eventId={} correlationId={} timestamp={}",
                event.getClass().getSimpleName(), event.eventId(), event.correlationId(), event.timestamp());
        applicationEventPublisher.publishEvent(event);
    }
}
