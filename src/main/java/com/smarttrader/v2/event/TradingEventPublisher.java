package com.smarttrader.v2.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around Spring's in-process ApplicationEventPublisher for TradingEvents,
 * per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md section 0.4. This is the single seam v2.5
 * subsystems (liquidity, positioning, siren, validation, feedback) publish through.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publish(TradingEvent event) {
        log.info("tradingEvent type={} eventId={} correlationId={} symbol={}",
                event.eventType, event.eventId, event.correlationId, event.symbol);
        publisher.publishEvent(event);
    }
}
