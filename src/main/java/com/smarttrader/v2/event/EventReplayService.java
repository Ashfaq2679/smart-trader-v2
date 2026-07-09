package com.smarttrader.v2.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * "Replay last N events" (V2_TECH_SPEC_v1.1.md section 11). Re-publishing is safe because
 * every DomainEvent's eventId is deterministic (section 9): consumers can dedupe replays
 * exactly like they'd dedupe a redelivered message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventReplayService {

    private final EventStore eventStore;
    private final DomainEventPublisher eventPublisher;

    /**
     * Re-publishes the last n recorded events, oldest first, and returns them.
     */
    public List<DomainEvent> replayLast(int n) {
        List<DomainEvent> events = eventStore.lastN(n);
        log.info("eventReplay replaying {} events", events.size());
        events.forEach(eventPublisher::publish);
        return events;
    }
}
