package com.smarttrader.v2.event;

import java.math.BigDecimal;

/**
 * Fired when a LiquidityPool is swept (trade beyond it, close back inside within 2 bars),
 * per V2_TECH_SPEC_v2.5.md section 3, rule 3. The primary trigger for Sweep-and-Reclaim
 * (section 5.2), SFP-Reversal (section 5.3), and Range-Harvester (section 5.5) - built in
 * Phase 2, not Phase 0.
 */
public class LiquiditySweepDetectedEvent extends TradingEvent {

    public BigDecimal level;
    /** "UP" (swept above a pool, e.g. EQH) or "DOWN" (swept below a pool, e.g. EQL). */
    public String side;
    public float density;
    public boolean reclaimed;

    public LiquiditySweepDetectedEvent() {
        super("liquidity.SweepDetected");
    }
}
