package com.smarttrader.v2.siren;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.event.DefensiveActionTakenEvent;
import com.smarttrader.v2.event.OpportunitySirenEvent;
import com.smarttrader.v2.event.TradingEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Opportunity;
import com.smarttrader.v2.model.Severity;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.model.TrendDirection;
import com.smarttrader.v2.strategy.PullbackStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpportunitySirenServiceTest {

    @Mock
    private TradingEventPublisher eventPublisher;

    @Mock
    private OpportunityRepository opportunityRepository;

    private final PullbackStrategy strategy = new PullbackStrategy();

    private OpportunitySirenService service() {
        return new OpportunitySirenService(eventPublisher, opportunityRepository);
    }

    private AnalysisContext.AnalysisContextBuilder base() {
        return AnalysisContext.builder()
                .price(100.0)
                .atr(2.0)
                .trendDirection(TrendDirection.SIDEWAYS)
                .nearestSupport(90.0)
                .nearestResistance(110.0)
                .cascadeActive(false)
                .fundingPercentile30d(40);
    }

    @Test
    void bullish_noneDirectionNeverPersistsOrPublishes() {
        AnalysisContext ctx = base().build();
        SignalResult signal = SignalResult.invalid("SomeStrategy");

        service().onSignalEvaluated("BTC-USD", strategy, signal, ctx);

        verifyNoInteractions(opportunityRepository, eventPublisher);
    }

    @Test
    void bearish_cascadeActiveIsAlwaysCriticalAndFiresDefensiveEvent() {
        AnalysisContext ctx = base().cascadeActive(true).build();
        SignalResult signal = validSignal(TradeDirection.LONG);
        when(opportunityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().onSignalEvaluated("BTC-USD", strategy, signal, ctx);

        ArgumentCaptor<TradingEvent> captor = ArgumentCaptor.forClass(TradingEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publish(captor.capture());

        OpportunitySirenEvent sirenEvent = (OpportunitySirenEvent) captor.getAllValues().get(0);
        assertThat(sirenEvent.severity).isEqualTo(Severity.CRITICAL);

        DefensiveActionTakenEvent defensiveEvent = (DefensiveActionTakenEvent) captor.getAllValues().get(1);
        assertThat(defensiveEvent.description).contains("no automated position management");
    }

    @Test
    void sideways_routineSignalWithoutCrowdOrCascadeIsInfoSeverity() {
        AnalysisContext ctx = base().fundingPercentile30d(40).build();
        SignalResult signal = validSignal(TradeDirection.LONG);
        when(opportunityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().onSignalEvaluated("BTC-USD", strategy, signal, ctx);

        ArgumentCaptor<OpportunitySirenEvent> captor = ArgumentCaptor.forClass(OpportunitySirenEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().severity).isEqualTo(Severity.INFO);
    }

    @Test
    void edgeCase_shortingAnExtremelyCrowdedLongFundingIsHighSeverity() {
        AnalysisContext ctx = base().fundingPercentile30d(97).build();
        SignalResult signal = validSignal(TradeDirection.SHORT);
        when(opportunityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().onSignalEvaluated("BTC-USD", strategy, signal, ctx);

        ArgumentCaptor<OpportunitySirenEvent> captor = ArgumentCaptor.forClass(OpportunitySirenEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().severity).isEqualTo(Severity.HIGH);
    }

    @Test
    void edgeCase_nonExecutableSignalStillPersistsAtFullSeverityWithRealDirection() {
        AnalysisContext ctx = base().fundingPercentile30d(97).build();
        SignalResult signal = SignalResult.builder()
                .valid(false)
                .strategyName("ShortSideStrategy")
                .direction(TradeDirection.SHORT)
                .entry(100.0)
                .stop(102.0)
                .target(94.0)
                .riskReward(3.0)
                .build();

        ArgumentCaptor<Opportunity> opportunityCaptor = ArgumentCaptor.forClass(Opportunity.class);
        when(opportunityRepository.save(opportunityCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service().onSignalEvaluated("BTC-USD", strategy, signal, ctx);

        Opportunity saved = opportunityCaptor.getValue();
        assertThat(saved.getDirection()).isEqualTo("SHORT");
        assertThat(saved.isExecutable()).isFalse();
        assertThat(saved.getSeverity()).isEqualTo(Severity.HIGH);
    }

    private SignalResult validSignal(TradeDirection direction) {
        return SignalResult.builder()
                .valid(true)
                .strategyName("PullbackStrategy")
                .direction(direction)
                .entry(100.0)
                .stop(95.0)
                .target(110.0)
                .riskReward(2.0)
                .build();
    }
}
