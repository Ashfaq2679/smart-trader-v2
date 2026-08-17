package com.smarttrader.v2.strategy.range;

/**
 * Immutable snapshot of a detected horizontal range.
 * Additive model per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL_WITH_RANGE_SR.md §2.3.1 lines 745-752.
 */
public record RangeStructure(
        RangeBoundary support,
        RangeBoundary resistance,
        double width,
        double widthAtrMultiple,
        double qualityScore,
        boolean valid
) {

    /** Sentinel for the "no valid range" case. */
    public static RangeStructure invalid() {
        RangeBoundary z = new RangeBoundary(0, 0, 0, 0, 0);
        return new RangeStructure(z, z, 0, 0, 0, false);
    }
}
