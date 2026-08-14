package com.smarttrader.v2.model;

import java.util.List;

/**
 * Snapshot of a symbol's liquidity pools at a point in time, per V2_TECH_SPEC_v2.5.md
 * section 3. Built by LiquidityMapperService (Phase 1A) and attached to AnalysisContext.
 */
public record LiquidityMap(List<LiquidityPool> pools, long asOfNs) {
}
