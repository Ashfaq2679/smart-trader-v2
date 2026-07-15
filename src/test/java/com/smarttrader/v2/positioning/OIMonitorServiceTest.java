package com.smarttrader.v2.positioning;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class OIMonitorServiceTest {

    private final OIMonitorService service = new OIMonitorService();

    @Test
    void edgeCase_noHistoryReturnsZeroChange() {
        assertThat(service.getOIChange1h("BTC-USD")).isZero();
        assertThat(service.getOIChange24h("BTC-USD")).isZero();
    }

    @Test
    void bullish_oiChange1hReflectsGrowthOverTheLastHour() {
        Instant twoHoursAgo = Instant.parse("2026-01-01T00:00:00Z");
        Instant oneHourAgo = twoHoursAgo.plusSeconds(3600);
        Instant now = oneHourAgo.plusSeconds(3600);

        service.storeOi("BTC-USD", 1000.0, twoHoursAgo);
        service.storeOi("BTC-USD", 1000.0, oneHourAgo);
        service.storeOi("BTC-USD", 1150.0, now);

        assertThat(service.getOIChange1h("BTC-USD")).isCloseTo(0.15, offset(0.001));
    }

    @Test
    void bearish_oiChange24hReflectsDeclineOverTheLastDay() {
        Instant dayAgo = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = dayAgo.plusSeconds(24 * 3600);

        service.storeOi("BTC-USD", 1000.0, dayAgo);
        service.storeOi("BTC-USD", 900.0, now);

        assertThat(service.getOIChange24h("BTC-USD")).isCloseTo(-0.10, offset(0.001));
    }

    @Test
    void edgeCase_noBaselineSampleOldEnoughReturnsZero() {
        Instant now = Instant.now();
        service.storeOi("BTC-USD", 1000.0, now); // only one very recent sample

        assertThat(service.getOIChange1h("BTC-USD")).isZero();
    }
}
