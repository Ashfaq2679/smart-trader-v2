package com.smarttrader.v2.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around Spring's in-process ApplicationEventPublisher. This is the single
 * seam for publishing DomainEvents; per CLAUDE.md's roadmap (Kafka Streams, candles.*
 * topics, trend.score/breakout.events/trade.decisions), a Kafka-backed implementation can
 * replace/augment this later without changing any producer call site.
 *
 * Also records every event into EventStore, backing section 11's "Replay last N events".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final EventStore eventStore;

    public void publish(DomainEvent event) {
        log.debug("event type={} eventId={} correlationId={} timestamp={}",
                event.getClass().getSimpleName(), event.eventId(), event.correlationId(), event.timestamp());
        eventStore.record(event);
        applicationEventPublisher.publishEvent(event);
    }
    
    @PostConstruct
    public void init() {
		log.info("DomainEventPublisher initialized with EventStore: {}", eventStore.getClass().getSimpleName());
    }
}
