package com.smarttrader.v2.event;

import com.smarttrader.v2.model.Severity;

/**
 * Fired whenever a playbook cell activates (a strategy produces a non-NONE direction),
 * per V2_TECH_SPEC_v2.5.md section 7 - "the system must never see an opportunity and
 * whisper." Fired at full severity regardless of executable/valid, so
 * NotificationFacadeService can alert on a real setup even when the venue can't take it.
 */
public class OpportunitySirenEvent extends TradingEvent {

    public String opportunityId;
    public String playbook;
    public String direction;
    public Severity severity;
    public boolean executable;
    public String reason;

    public OpportunitySirenEvent() {
        super("siren.OpportunityDetected");
    }
}
