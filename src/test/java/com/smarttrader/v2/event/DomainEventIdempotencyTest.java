package com.smarttrader.v2.event;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.MarketRegime;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every DomainEvent must be idempotent (V2_TECH_SPEC_v1.1.md section 9): the same
 * occurrence, described by the same business key, must always produce the same eventId
 * so a consumer can dedupe retries/replays.
 */
class DomainEventIdempotencyTest {

    @Test
    void candleUpdatedEventIsIdempotentForTheSameCandle() {
        Candle candle = Candle.builder().timestamp(Instant.ofEpochSecond(100))
                .open(1).high(2).low(0.5).close(1.5).volume(10).build();

        CandleUpdatedEvent first = CandleUpdatedEvent.of("BTC-USD", Granularity.ONE_HOUR, candle);
        CandleUpdatedEvent second = CandleUpdatedEvent.of("BTC-USD", Granularity.ONE_HOUR, candle);

        assertThat(first.eventId()).isEqualTo(second.eventId());
        assertThat(first.correlationId()).isEqualTo(second.correlationId());
    }

    @Test
    void candleUpdatedEventDiffersForADifferentCandle() {
        Candle candleA = Candle.builder().timestamp(Instant.ofEpochSecond(100))
                .open(1).high(2).low(0.5).close(1.5).volume(10).build();
        Candle candleB = Candle.builder().timestamp(Instant.ofEpochSecond(200))
                .open(1).high(2).low(0.5).close(1.5).volume(10).build();

        CandleUpdatedEvent eventA = CandleUpdatedEvent.of("BTC-USD", Granularity.ONE_HOUR, candleA);
        CandleUpdatedEvent eventB = CandleUpdatedEvent.of("BTC-USD", Granularity.ONE_HOUR, candleB);

        assertThat(eventA.eventId()).isNotEqualTo(eventB.eventId());
    }

    @Test
    void regimeDetectedEventIsIdempotentForTheSameCorrelationIdAndProduct() {
        RegimeDetectedEvent first = RegimeDetectedEvent.of("corr-1", "BTC-USD", MarketRegime.PULLBACK, 0.8);
        RegimeDetectedEvent second = RegimeDetectedEvent.of("corr-1", "BTC-USD", MarketRegime.PULLBACK, 0.9);

        // eventId keys off (correlationId, productId), not confidence: a retry with the
        // same correlationId is treated as the same occurrence.
        assertThat(first.eventId()).isEqualTo(second.eventId());
    }

    @Test
    void everyEventCarriesATimestampAndCorrelationId() {
        RegimeDetectedEvent event = RegimeDetectedEvent.of("corr-2", "ETH-USD", MarketRegime.BREAKOUT, 0.5);

        assertThat(event.timestamp()).isNotNull();
        assertThat(event.correlationId()).isEqualTo("corr-2");
        assertThat(event.eventId()).isNotBlank();
    }
}
