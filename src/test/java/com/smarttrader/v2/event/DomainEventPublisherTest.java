package com.smarttrader.v2.event;

import com.smarttrader.v2.model.MarketRegime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private EventStore eventStore;
    private DomainEventPublisher domainEventPublisher;

    @BeforeEach
    void setUp() {
        eventStore = new EventStore(1000);
        domainEventPublisher = new DomainEventPublisher(applicationEventPublisher, eventStore);
    }

    @Test
    void publishDelegatesToSpringApplicationEventPublisher() {
        RegimeDetectedEvent event = RegimeDetectedEvent.of("corr-1", "BTC-USD", MarketRegime.PULLBACK, 0.8);

        domainEventPublisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }

    @Test
    void publishRecordsEventIntoTheEventStore() {
        RegimeDetectedEvent event = RegimeDetectedEvent.of("corr-2", "ETH-USD", MarketRegime.BREAKOUT, 0.6);

        domainEventPublisher.publish(event);

        assertThat(eventStore.lastN(10)).containsExactly(event);
    }
}
