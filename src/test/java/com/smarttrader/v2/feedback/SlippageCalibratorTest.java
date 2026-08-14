package com.smarttrader.v2.feedback;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarttrader.v2.event.ConfigChangedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.ConfigChangeRecord;
import com.smarttrader.v2.model.TradeOutcome;
import com.smarttrader.v2.validation.TradeOutcomeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlippageCalibratorTest {

    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;
    @Mock
    private ConfigChangeRepository configChangeRepository;
    @Mock
    private TradingEventPublisher eventPublisher;

    private SlippageModelService slippageModelService;
    private SlippageCalibrator calibrator;

    private SlippageCalibrator build(List<String> symbols) {
        slippageModelService = new SlippageModelService(1.0);
        calibrator = new SlippageCalibrator(tradeOutcomeRepository, slippageModelService, configChangeRepository, eventPublisher);
        ReflectionTestUtils.setField(calibrator, "trackedSymbols", symbols);
        return calibrator;
    }

    private TradeOutcome outcome(double slippageMultiple) {
        return TradeOutcome.builder().slippageMultiple(slippageMultiple).build();
    }

    @Test
    void bearish_realizedSlippageOverTwiceModeledUpdatesModelAndPublishesEvent() {
        when(tradeOutcomeRepository.findTop100BySymbolOrderByClosedAtDesc("BTC-USD"))
                .thenReturn(List.of(outcome(3.0), outcome(3.0), outcome(3.0)));

        build(List.of("BTC-USD")).calibrateSlippage();

        assertThat(slippageModelService.getModeledSlippage("BTC-USD")).isEqualTo(3.0);

        ArgumentCaptor<ConfigChangeRecord> recordCaptor = ArgumentCaptor.forClass(ConfigChangeRecord.class);
        verify(configChangeRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getConfigKey()).isEqualTo("slippage_model");
        assertThat(recordCaptor.getValue().getSymbol()).isEqualTo("BTC-USD");

        ArgumentCaptor<ConfigChangedEvent> eventCaptor = ArgumentCaptor.forClass(ConfigChangedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().configKey).isEqualTo("slippage_model");
    }

    @Test
    void bullish_realizedSlippageWithinModelDoesNotUpdate() {
        when(tradeOutcomeRepository.findTop100BySymbolOrderByClosedAtDesc("BTC-USD"))
                .thenReturn(List.of(outcome(1.1), outcome(1.0), outcome(0.9)));

        build(List.of("BTC-USD")).calibrateSlippage();

        assertThat(slippageModelService.getModeledSlippage("BTC-USD")).isEqualTo(1.0);
        verify(configChangeRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void edgeCase_noOutcomesForSymbolSkipsGracefully() {
        when(tradeOutcomeRepository.findTop100BySymbolOrderByClosedAtDesc("BTC-USD")).thenReturn(List.of());

        build(List.of("BTC-USD")).calibrateSlippage();

        verify(configChangeRepository, never()).save(any());
    }
}
