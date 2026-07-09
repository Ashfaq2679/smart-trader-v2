package com.smarttrader.v2.event;

import com.smarttrader.v2.model.MarketRegime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventStoreTest {

    private RegimeDetectedEvent eventFor(int i) {
        return RegimeDetectedEvent.of("corr-" + i, "BTC-USD", MarketRegime.PULLBACK, 0.5);
    }

    @Test
    void bullish_lastNReturnsMostRecentEventsInOrder() {
        EventStore store = new EventStore(10);
        for (int i = 0; i < 5; i++) {
            store.record(eventFor(i));
        }

        assertThat(store.lastN(3)).extracting(DomainEvent::correlationId)
                .containsExactly("corr-2", "corr-3", "corr-4");
    }

    @Test
    void bearish_requestingMoreThanAvailableReturnsWhatExists() {
        EventStore store = new EventStore(10);
        store.record(eventFor(0));

        assertThat(store.lastN(50)).hasSize(1);
    }

    @Test
    void edgeCase_bufferEvictsOldestOnceMaxSizeExceeded() {
        EventStore store = new EventStore(3);
        for (int i = 0; i < 5; i++) {
            store.record(eventFor(i));
        }

        assertThat(store.size()).isEqualTo(3);
        assertThat(store.lastN(10)).extracting(DomainEvent::correlationId)
                .containsExactly("corr-2", "corr-3", "corr-4");
    }
}
