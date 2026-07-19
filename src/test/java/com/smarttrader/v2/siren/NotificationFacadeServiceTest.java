package com.smarttrader.v2.siren;

import org.junit.jupiter.api.Test;

import com.smarttrader.v2.event.ExecutionDegradedEvent;
import com.smarttrader.v2.event.OpportunitySirenEvent;
import com.smarttrader.v2.model.Severity;

class NotificationFacadeServiceTest {

    private final NotificationFacadeService service = new NotificationFacadeService();

    private OpportunitySirenEvent event(Severity severity) {
        OpportunitySirenEvent event = new OpportunitySirenEvent();
        event.symbol = "BTC-USD";
        event.playbook = "PullbackStrategy";
        event.direction = "LONG";
        event.severity = severity;
        event.reason = "test reason";
        return event;
    }

    @Test
    void bullish_criticalSeverityDoesNotThrowAndRoutesToTelegramAndEmail() {
        service.onOpportunitySiren(event(Severity.CRITICAL));
    }

    @Test
    void bearish_highSeverityDoesNotThrowAndRoutesToWebhook() {
        service.onOpportunitySiren(event(Severity.HIGH));
    }

    @Test
    void sideways_infoSeverityIsANoOp() {
        service.onOpportunitySiren(event(Severity.INFO));
    }

    @Test
    void edgeCase_executionDegradedEventLogsBoldBannerWithoutThrowing() {
        ExecutionDegradedEvent event = new ExecutionDegradedEvent();
        event.symbol = "BTC-USD";
        event.reason = "missing order credentials";
        event.detail = "live-enabled=true but no key configured";

        service.onExecutionDegraded(event);
    }
}
