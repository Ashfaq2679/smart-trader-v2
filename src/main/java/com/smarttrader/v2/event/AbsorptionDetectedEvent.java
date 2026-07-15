package com.smarttrader.v2.event;

/**
 * Fired when heavy taker flow on one side fails to move price proportionally, per
 * V2_TECH_SPEC_v2.5.md section 4 ("Absorption"): someone big is absorbing the flow.
 */
public class AbsorptionDetectedEvent extends TradingEvent {

    /** "BID" (absorbing sell-taker flow, i.e. someone is buying the dip) or "ASK" (mirror, selling the rip). */
    public String side;
    public double relativeVolume;
    public double priceDeclineAtr;

    public AbsorptionDetectedEvent() {
        super("positioning.AbsorptionDetected");
    }
}
