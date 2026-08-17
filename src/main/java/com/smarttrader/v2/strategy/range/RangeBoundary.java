package com.smarttrader.v2.strategy.range;

/**
 * Immutable value describing a single support or resistance boundary of a horizontal range.
 * Additive model per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL_WITH_RANGE_SR.md §2.3.1 lines 733-739.
 *
 * <ul>
 *   <li>{@code lower}/{@code upper}: the boundary zone's outer edges (price band, not a single tick).</li>
 *   <li>{@code midpoint}: the price used for target/entry calculations.</li>
 *   <li>{@code touches}: number of validated reactions off this boundary.</li>
 *   <li>{@code strength}: normalized 0-100 relative strength of this boundary.</li>
 * </ul>
 */
public record RangeBoundary(
        double lower,
        double upper,
        double midpoint,
        int touches,
        double strength
) {
}
