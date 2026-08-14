package com.smarttrader.v2.siren;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.constants.SirenConstants;
import com.smarttrader.v2.event.DefensiveActionTakenEvent;
import com.smarttrader.v2.event.OpportunitySirenEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Opportunity;
import com.smarttrader.v2.model.Severity;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.strategy.TradingStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Opportunity Siren, per V2_TECH_SPEC_v2.5.md section 7: "The system must never see an
 * opportunity and whisper." Every playbook-cell activation - a strategy producing a real
 * direction, whether or not it ends up executable - is persisted and published at full
 * severity, so a non-executable short on a long-only venue still leaves "the exact thesis
 * and levels" on record instead of going silent.
 *
 * Callers should invoke onSignalEvaluated only when signal.direction() != NONE: that
 * reliably means a playbook cell activated across every current strategy (a NONE
 * direction just means "no pattern here", which section 7 never asked to be sirened).
 *
 * SignalResult has no reason() field in this codebase's reset baseline (unlike the plan's
 * code sample), so the reason string is synthesized here from the signal's own levels.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunitySirenService {

    private final TradingEventPublisher eventPublisher;
    private final OpportunityRepository opportunityRepository;

    public void onSignalEvaluated(String symbol, TradingStrategy strategy, SignalResult signal, AnalysisContext ctx) {
        if (signal.direction() == TradeDirection.NONE) {
            return;
        }

        Severity severity = calculateSeverity(ctx, signal);
        String reason = buildReason(signal);

        Instant now = Instant.now();
        Opportunity opportunity = Opportunity.builder()
                .symbol(symbol)
                .playbook(strategy.getClass().getSimpleName())
                .direction(signal.direction().name())
                .severity(severity)
                .executable(signal.valid())
                .reason(reason)
                .entry(signal.entry())
                .stop(signal.stop())
                .target(signal.target())
                .contextSnapshot(snapshot(ctx))
                .createdAtNs(System.nanoTime())
                .expiresAt(now.plus(SirenConstants.OPPORTUNITY_TTL_DAYS, ChronoUnit.DAYS))
                .scored(false)
                .build();

        opportunity = opportunityRepository.save(opportunity);

        OpportunitySirenEvent event = new OpportunitySirenEvent();
        event.symbol = symbol;
        event.opportunityId = opportunity.getId();
        event.playbook = opportunity.getPlaybook();
        event.direction = opportunity.getDirection();
        event.severity = severity;
        event.executable = opportunity.isExecutable();
        event.reason = reason;
        eventPublisher.publish(event);

        log.info("opportunitySiren symbol={} playbook={} direction={} severity={} executable={}",
                symbol, opportunity.getPlaybook(), opportunity.getDirection(), severity, opportunity.isExecutable());

        if (severity == Severity.CRITICAL) {
            DefensiveActionTakenEvent defensiveEvent = new DefensiveActionTakenEvent();
            defensiveEvent.symbol = symbol;
            defensiveEvent.opportunityId = opportunity.getId();
            defensiveEvent.description = "CRITICAL opportunity detected; no automated position "
                    + "management is wired up in this codebase yet, so no defensive action was taken";
            eventPublisher.publish(defensiveEvent);
        }
    }

    /**
     * CRITICAL: an active liquidation cascade (regime-breakdown territory).
     * HIGH: fading a crowd sitting at a funding extreme (counter-crowd confluence).
     * INFO: everything else - a routine playbook activation.
     */
    private Severity calculateSeverity(AnalysisContext ctx, SignalResult signal) {
        if (ctx.cascadeActive()) {
            return Severity.CRITICAL;
        }

        boolean shortingCrowdedLongs = signal.direction() == TradeDirection.SHORT
                && ctx.fundingPercentile30d() >= SirenConstants.CROWD_FADE_HIGH_FUNDING_PERCENTILE;
        boolean longingCrowdedShorts = signal.direction() == TradeDirection.LONG
                && ctx.fundingPercentile30d() <= SirenConstants.CROWD_FADE_LOW_FUNDING_PERCENTILE;

        if (shortingCrowdedLongs || longingCrowdedShorts) {
            return Severity.HIGH;
        }

        return Severity.INFO;
    }

    private String buildReason(SignalResult signal) {
        return "%s %s entry=%.4f stop=%.4f target=%.4f rr=%.2f executable=%s".formatted(
                signal.strategyName(), signal.direction(), signal.entry(), signal.stop(),
                signal.target(), signal.riskReward(), signal.valid());
    }

    private Map<String, Object> snapshot(AnalysisContext ctx) {
        Map<String, Object> map = new HashMap<>();
        map.put("price", ctx.price());
        map.put("atr", ctx.atr());
        map.put("trendDirection", ctx.trendDirection());
        map.put("trendStrength", ctx.trendStrength());
        map.put("nearestSupport", ctx.nearestSupport());
        map.put("nearestResistance", ctx.nearestResistance());
        map.put("cvdSlope5m", ctx.cvdSlope5m());
        map.put("cvdDivergence", ctx.cvdDivergence());
        map.put("fundingRateBps", ctx.fundingRateBps());
        map.put("fundingPercentile30d", ctx.fundingPercentile30d());
        map.put("oiChange1h", ctx.oiChange1h());
        map.put("oiChange24h", ctx.oiChange24h());
        map.put("cascadeActive", ctx.cascadeActive());
        return map;
    }
}
