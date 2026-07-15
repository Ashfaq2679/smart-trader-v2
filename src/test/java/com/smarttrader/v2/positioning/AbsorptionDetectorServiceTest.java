package com.smarttrader.v2.positioning;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.event.AbsorptionDetectedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AbsorptionDetectorServiceTest {

    @Mock
    private TradingEventPublisher eventPublisher;

    private AbsorptionDetectorService service;
    private final AnalysisContext ctx = AnalysisContext.builder().atr(10.0).build();

    @BeforeEach
    void setUp() {
        service = new AbsorptionDetectorService(eventPublisher);
    }

    private Candle candle(double open, double close, double volume) {
        return Candle.builder().timestamp(Instant.now())
                .open(open).high(Math.max(open, close)).low(Math.min(open, close)).close(close).volume(volume).build();
    }

    @Test
    void bullish_heavySellVolumeWithMinimalDeclineIsBidAbsorption() {
        // open=100 close=99 (1% decline -> priceDeclineAtr = 1/10 = 0.1 < 0.25), volume=300
        // relativeVolume = 300 * (1 - 99/100) = 3.0 > 2.5
        Candle candle = candle(100, 99, 300);

        service.onCandleClose("BTC-USD", candle, ctx);

        ArgumentCaptor<AbsorptionDetectedEvent> captor = ArgumentCaptor.forClass(AbsorptionDetectedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().side).isEqualTo("BID");
        assertThat(captor.getValue().symbol).isEqualTo("BTC-USD");
    }

    @Test
    void bearish_heavyBuyVolumeWithMinimalRiseIsAskAbsorption() {
        Candle candle = candle(99, 100, 300); // mirror of the bid case

        service.onCandleClose("BTC-USD", candle, ctx);

        ArgumentCaptor<AbsorptionDetectedEvent> captor = ArgumentCaptor.forClass(AbsorptionDetectedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().side).isEqualTo("ASK");
    }

    @Test
    void sideways_normalVolumeCandleNeverTriggersAbsorption() {
        Candle candle = candle(100, 99, 5); // low volume, doesn't clear 2.5x relative volume threshold

        service.onCandleClose("BTC-USD", candle, ctx);

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void edgeCase_bigDeclineWithHeavyVolumeIsNotAbsorptionBecausePriceActuallyMoved() {
        // Large decline (open=100, close=70 -> 3.0 ATR decline) disqualifies it as absorption
        Candle candle = candle(100, 70, 300);

        service.onCandleClose("BTC-USD", candle, ctx);

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void edgeCase_flatCandleNeitherRisesNorFallsSoNoAbsorptionCheckRuns() {
        Candle candle = candle(100, 100, 1000);

        service.onCandleClose("BTC-USD", candle, ctx);

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void edgeCase_zeroAtrSkipsDetectionToAvoidDivisionByZero() {
        AnalysisContext zeroAtrCtx = AnalysisContext.builder().atr(0.0).build();
        Candle candle = candle(100, 99, 300);

        service.onCandleClose("BTC-USD", candle, zeroAtrCtx);

        verify(eventPublisher, never()).publish(any());
    }
}
