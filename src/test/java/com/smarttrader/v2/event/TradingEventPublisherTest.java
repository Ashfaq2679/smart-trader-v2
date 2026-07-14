package com.smarttrader.v2.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Phase 0.7 gate: "Spring Events infrastructure loads (ApplicationEventPublisher wiring)". */
@ExtendWith(MockitoExtension.class)
class TradingEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private TradingEventPublisher tradingEventPublisher;

    @BeforeEach
    void setUp() {
        tradingEventPublisher = new TradingEventPublisher(applicationEventPublisher);
    }

    @Test
    void bullish_publishDelegatesToSpringApplicationEventPublisher() {
        LiquiditySweepDetectedEvent event = new LiquiditySweepDetectedEvent();
        event.symbol = "BTC-USD";
        event.side = "DOWN";
        event.density = 60f;

        tradingEventPublisher.publish(event);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isSameAs(event);
    }

    @Test
    void bullish_everyEventGetsAnEventIdAndTimestampOnConstruction() {
        LiquiditySweepDetectedEvent event = new LiquiditySweepDetectedEvent();

        assertThat(event.eventId).isNotBlank();
        assertThat(event.timestampNs).isPositive();
        assertThat(event.eventType).isEqualTo("liquidity.SweepDetected");
        assertThat(event.schemaVersion).isEqualTo(1);
    }

    @Test
    void edgeCase_twoEventsGetDistinctEventIds() {
        LiquiditySweepDetectedEvent first = new LiquiditySweepDetectedEvent();
        LiquiditySweepDetectedEvent second = new LiquiditySweepDetectedEvent();

        assertThat(first.eventId).isNotEqualTo(second.eventId);
    }
}
