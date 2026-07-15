package com.smarttrader.v2.siren;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.smarttrader.v2.event.OpportunitySirenEvent;
import com.smarttrader.v2.model.Severity;

import lombok.extern.slf4j.Slf4j;

/**
 * Fan-out for OpportunitySirenEvent, per V2_TECH_SPEC_v2.5.md section 7 / Phase 3.2.
 *
 * Channel wiring (Telegram/email/webhook HTTP clients, credentials) isn't part of this
 * codebase yet, so each channel method is a documented stub that logs instead of calling
 * out - never hardcode credentials for services that don't exist here, per project rules.
 */
@Slf4j
@Service
public class NotificationFacadeService {

    @EventListener
    public void onOpportunitySiren(OpportunitySirenEvent event) {
        if (event.severity == Severity.CRITICAL) {
            sendTelegram(event);
            sendEmail(event);
        } else if (event.severity == Severity.HIGH) {
            sendWebhook(event);
        }
    }

    private void sendTelegram(OpportunitySirenEvent event) {
        log.info("notification channel=telegram symbol={} playbook={} direction={} severity={} reason={}",
                event.symbol, event.playbook, event.direction, event.severity, event.reason);
    }

    private void sendEmail(OpportunitySirenEvent event) {
        log.info("notification channel=email symbol={} playbook={} direction={} severity={} reason={}",
                event.symbol, event.playbook, event.direction, event.severity, event.reason);
    }

    private void sendWebhook(OpportunitySirenEvent event) {
        log.info("notification channel=webhook symbol={} playbook={} direction={} severity={} reason={}",
                event.symbol, event.playbook, event.direction, event.severity, event.reason);
    }
}
