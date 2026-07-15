package com.smarttrader.v2.event;

/**
 * Fired alongside a CRITICAL OpportunitySirenEvent, per V2_TECH_SPEC_v2.5.md section 7.
 *
 * This codebase has no position-management system yet (no PositionService,
 * OrderExecutionService, or equivalent - confirmed absent from this v2.5 baseline), so
 * "defensive action" here is honestly descriptive, not prescriptive: description always
 * states that no automated position action was taken, only that a CRITICAL condition was
 * detected. Once a real position manager exists, that service can listen for this event
 * and act; today it exists purely so the event contract/audit trail is in place ahead of
 * that capability (Phase 4+).
 */
public class DefensiveActionTakenEvent extends TradingEvent {

    public String opportunityId;
    public String description;

    public DefensiveActionTakenEvent() {
        super("siren.DefensiveActionTaken");
    }
}
