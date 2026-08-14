package com.smarttrader.v2.validation;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.model.ShadowSignal;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShadowModeServiceTest {

    @Mock
    private ShadowSignalRepository repository;

    private ShadowModeService service() {
        return new ShadowModeService(repository);
    }

    @Test
    void bullish_logShadowSignalPersistsFlattenedSignalFields() {
        SignalResult signal = SignalResult.builder()
                .valid(true).strategyName("PullbackStrategy").direction(TradeDirection.LONG)
                .entry(100).stop(95).target(110).riskReward(2.0).build();

        service().logShadowSignal("PullbackStrategy", "BTC-USD", signal);

        ArgumentCaptor<ShadowSignal> captor = ArgumentCaptor.forClass(ShadowSignal.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDirection()).isEqualTo("LONG");
        assertThat(captor.getValue().getEntry()).isEqualTo(100);
        assertThat(captor.getValue().isValid()).isTrue();
    }

    @Test
    void bearish_noSignalsInLookbackReturnsEmptyMetrics() {
        when(repository.findByStrategyNameAndSymbolAndDetectedAtAfterOrderByDetectedAtAsc(anyString(), anyString(), any()))
                .thenReturn(List.of());

        ShadowMetrics metrics = service().getMetrics("PullbackStrategy", "BTC-USD");

        assertThat(metrics.signalCount()).isZero();
        assertThat(metrics.age()).isEqualTo(java.time.Duration.ZERO);
    }

    @Test
    void sideways_signalCountAndAgeReflectRealPersistedSignals() {
        Instant oldest = Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS);
        ShadowSignal first = ShadowSignal.builder().detectedAt(oldest).build();
        ShadowSignal second = ShadowSignal.builder().detectedAt(Instant.now()).build();
        when(repository.findByStrategyNameAndSymbolAndDetectedAtAfterOrderByDetectedAtAsc(anyString(), anyString(), any()))
                .thenReturn(List.of(first, second));

        ShadowMetrics metrics = service().getMetrics("PullbackStrategy", "BTC-USD");

        assertThat(metrics.signalCount()).isEqualTo(2);
        assertThat(metrics.age().toDays()).isEqualTo(10);
    }

    @Test
    void edgeCase_distributionMatchNeverFakesConfidenceWithoutBacktestReference() {
        ShadowSignal signal = ShadowSignal.builder().detectedAt(Instant.now()).build();
        when(repository.findByStrategyNameAndSymbolAndDetectedAtAfterOrderByDetectedAtAsc(anyString(), anyString(), any()))
                .thenReturn(List.of(signal));

        ShadowMetrics metrics = service().getMetrics("PullbackStrategy", "BTC-USD");

        assertThat(metrics.distributionMatch()).isEqualTo(0.0);
    }
}
