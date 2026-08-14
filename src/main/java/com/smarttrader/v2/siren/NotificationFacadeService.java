package com.smarttrader.v2.siren;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.smarttrader.v2.event.ExecutionDegradedEvent;
import com.smarttrader.v2.event.OpportunitySirenEvent;
import com.smarttrader.v2.model.Severity;

import lombok.extern.slf4j.Slf4j;

/**
 * Fan-out for OpportunitySirenEvent, per V2_TECH_SPEC_v2.5.md section 7 / Phase 3.2, and
 * for ExecutionDegradedEvent (execution-layer rebuild): the system's stated target is
 * "analyze, then place market orders based on the decision" - if it can't do that while
 * live trading is supposed to be on, that's never allowed to be a quiet log line.
 *
 * Channel wiring (Telegram/email/webhook HTTP clients, credentials) isn't part of this
 * codebase yet, so each channel method is a documented stub that logs instead of calling
 * out - never hardcode credentials for services that don't exist here, per project rules.
 */
@Slf4j
@Service
public class NotificationFacadeService {

    private static final String BOLD_BANNER = "*".repeat(90);

    @EventListener
    public void onOpportunitySiren(OpportunitySirenEvent event) {
        if (event.severity == Severity.CRITICAL) {
            sendTelegram(event);
            sendEmail(event);
        } else if (event.severity == Severity.HIGH) {
            sendWebhook(event);
        }
    }

    @EventListener
    public void onExecutionDegraded(ExecutionDegradedEvent event) {
        log.error("\n{}\n**  LIVE EXECUTION DEGRADED  **  symbol={}  reason={}  detail={}\n{}",
                BOLD_BANNER, event.symbol, event.reason, event.detail, BOLD_BANNER);
        sendTelegramDegraded(event);
        sendEmailDegraded(event);
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

    private void sendTelegramDegraded(ExecutionDegradedEvent event) {
        log.info("notification channel=telegram BOLD symbol={} reason={} detail={}",
                event.symbol, event.reason, event.detail);
    }

    private void sendEmailDegraded(ExecutionDegradedEvent event) {
        log.info("notification channel=email BOLD symbol={} reason={} detail={}",
                event.symbol, event.reason, event.detail);
    }
}
