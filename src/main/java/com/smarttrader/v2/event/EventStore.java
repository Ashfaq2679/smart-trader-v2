package com.smarttrader.v2.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Bounded in-memory log of every published DomainEvent, backing "Replay last N events"
 * (V2_TECH_SPEC_v1.1.md section 11). In-memory only: like the other section 11/6 stores,
 * a durable version would need its own MongoDB collection; this is sufficient to recover
 * from a same-process failure but not a full restart.
 */
@Component
public class EventStore {

    private final ConcurrentLinkedDeque<DomainEvent> buffer = new ConcurrentLinkedDeque<>();
    private final int maxSize;

    public EventStore(@Value("${events.replay-buffer-size:1000}") int maxSize) {
        this.maxSize = maxSize;
    }

    public void record(DomainEvent event) {
        buffer.addLast(event);
        while (buffer.size() > maxSize) {
            buffer.pollFirst();
        }
    }

    /** The last n recorded events, oldest first. */
    public List<DomainEvent> lastN(int n) {
        List<DomainEvent> snapshot = new ArrayList<>(buffer);
        int from = Math.max(0, snapshot.size() - n);
        return Collections.unmodifiableList(snapshot.subList(from, snapshot.size()));
    }

    public int size() {
        return buffer.size();
    }
}
