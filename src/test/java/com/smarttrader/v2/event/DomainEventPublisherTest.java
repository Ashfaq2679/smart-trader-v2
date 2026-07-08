package com.smarttrader.v2.event;

import com.smarttrader.v2.model.MarketRegime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private DomainEventPublisher domainEventPublisher;

    @BeforeEach
    void setUp() {
        domainEventPublisher = new DomainEventPublisher(applicationEventPublisher);
    }

    @Test
    void publishDelegatesToSpringApplicationEventPublisher() {
        RegimeDetectedEvent event = RegimeDetectedEvent.of("corr-1", "BTC-USD", MarketRegime.PULLBACK, 0.8);

        domainEventPublisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }
}
