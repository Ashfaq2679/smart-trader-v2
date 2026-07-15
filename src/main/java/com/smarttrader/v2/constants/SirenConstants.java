package com.smarttrader.v2.constants;

/**
 * Constants for the Opportunity Siren (V2_TECH_SPEC_v2.5.md section 7), kept separate
 * from other v2.5 subsystem constants for the same reason as LiquidityConstants/
 * PositioningConstants/PlaybookConstants - a distinct tunable subsystem, "validated: false"
 * per section 12 until real sensitivity analysis.
 */
public final class SirenConstants {

    /** Opportunity TTL, per section 7 / Phase 0 Mongo schema: "opportunities (TTL: 180 days)". */
    public static final int OPPORTUNITY_TTL_DAYS = 180;

    /** Funding percentile above which a crowd is "extremely" long (counter-crowd short severity gate). */
    public static final double CROWD_FADE_HIGH_FUNDING_PERCENTILE = 95;

    /** Funding percentile below which a crowd is "extremely" short (counter-crowd long severity gate). */
    public static final double CROWD_FADE_LOW_FUNDING_PERCENTILE = 5;

    /** Post-hoc scoring horizons, in hours, per section 7. */
    public static final int SCORING_HORIZON_1H = 1;
    public static final int SCORING_HORIZON_4H = 4;
    public static final int SCORING_HORIZON_24H = 24;

    private SirenConstants() {
    }
}
