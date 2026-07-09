package com.smarttrader.v2.event;

import com.smarttrader.v2.model.MarketRegime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventReplayServiceTest {

    @Mock
    private EventStore eventStore;
    @Mock
    private DomainEventPublisher eventPublisher;

    private EventReplayService replayService;

    @BeforeEach
    void setUp() {
        replayService = new EventReplayService(eventStore, eventPublisher);
    }

    @Test
    void replaysEveryStoredEventInOrderThroughThePublisher() {
        RegimeDetectedEvent e1 = RegimeDetectedEvent.of("corr-1", "BTC-USD", MarketRegime.PULLBACK, 0.5);
        RegimeDetectedEvent e2 = RegimeDetectedEvent.of("corr-2", "BTC-USD", MarketRegime.BREAKOUT, 0.7);
        org.mockito.Mockito.when(eventStore.lastN(2)).thenReturn(List.of(e1, e2));

        List<DomainEvent> replayed = replayService.replayLast(2);

        assertThat(replayed).containsExactly(e1, e2);
        verify(eventPublisher).publish(e1);
        verify(eventPublisher).publish(e2);
        verify(eventPublisher, times(2)).publish(org.mockito.ArgumentMatchers.any());
    }
}
