package com.smarttrader.v2.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.event.StrategyStageChangedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.StrategyStage;
import com.smarttrader.v2.model.StrategyState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyStateManagerTest {

    @Mock
    private StrategyStateRepository repository;

    @Mock
    private TradingEventPublisher eventPublisher;

    private StrategyStateManager manager() {
        return new StrategyStateManager(repository, eventPublisher);
    }

    @Test
    void bullish_getStageDefaultsToResearchWhenNoStateExists() {
        when(repository.findByStrategyNameAndSymbol("PullbackStrategy", "BTC-USD")).thenReturn(null);

        assertThat(manager().getStage("PullbackStrategy", "BTC-USD")).isEqualTo(StrategyStage.RESEARCH);
    }

    @Test
    void bullish_promoteMovesResearchToShadowAndPublishesEvent() {
        when(repository.findByStrategyNameAndSymbol("PullbackStrategy", "BTC-USD")).thenReturn(null);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        manager().promote("PullbackStrategy", "BTC-USD", StrategyStage.SHADOW, "backtest passed");

        ArgumentCaptor<StrategyState> stateCaptor = ArgumentCaptor.forClass(StrategyState.class);
        verify(repository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getStage()).isEqualTo(StrategyStage.SHADOW);

        ArgumentCaptor<StrategyStageChangedEvent> eventCaptor = ArgumentCaptor.forClass(StrategyStageChangedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().oldStage).isEqualTo(StrategyStage.RESEARCH);
        assertThat(eventCaptor.getValue().newStage).isEqualTo(StrategyStage.SHADOW);
    }

    @Test
    void bearish_demoteMovesMicroLiveToShadowAndPublishesEvent() {
        StrategyState existing = StrategyState.builder()
                .strategyName("PullbackStrategy").symbol("BTC-USD").stage(StrategyStage.MICRO_LIVE).build();
        when(repository.findByStrategyNameAndSymbol("PullbackStrategy", "BTC-USD")).thenReturn(existing);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        manager().demote("PullbackStrategy", "BTC-USD", StrategyStage.SHADOW, "slippage too high");

        assertThat(existing.getStage()).isEqualTo(StrategyStage.SHADOW);
        verify(eventPublisher).publish(any(StrategyStageChangedEvent.class));
    }

    @Test
    void edgeCase_promoteToLowerOrEqualStageThrowsAndNeverPublishes() {
        StrategyState existing = StrategyState.builder()
                .strategyName("PullbackStrategy").symbol("BTC-USD").stage(StrategyStage.SHADOW).build();
        when(repository.findByStrategyNameAndSymbol("PullbackStrategy", "BTC-USD")).thenReturn(existing);

        assertThatThrownBy(() -> manager().promote("PullbackStrategy", "BTC-USD", StrategyStage.RESEARCH, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void edgeCase_demoteToHigherOrEqualStageThrowsAndNeverPublishes() {
        StrategyState existing = StrategyState.builder()
                .strategyName("PullbackStrategy").symbol("BTC-USD").stage(StrategyStage.SHADOW).build();
        when(repository.findByStrategyNameAndSymbol("PullbackStrategy", "BTC-USD")).thenReturn(existing);

        assertThatThrownBy(() -> manager().demote("PullbackStrategy", "BTC-USD", StrategyStage.FULL, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventPublisher);
    }
}
