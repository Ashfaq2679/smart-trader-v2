package com.smarttrader.v2.model;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * A detected sweep of a LiquidityPool (trade beyond the pool followed by a close back
 * inside), per V2_TECH_SPEC_v2.5.md section 3, rule 3. Feeds AnalysisContext.recentSweeps
 * and is the primary trigger for the Sweep-and-Reclaim / SFP-Reversal / Range-Harvester
 * strategies (Phase 2).
 */
@Data
@Builder
public class OpportunitySweep {

    private String symbol;

    private BigDecimal level;

    /** "UP" (swept above a pool, e.g. EQH) or "DOWN" (swept below a pool, e.g. EQL). */
    private String side;

    private float density;

    /** True once price closed back inside the pool within 2 bars of the sweep. */
    private boolean reclaimed;

    private long detectedAtNs;
}
