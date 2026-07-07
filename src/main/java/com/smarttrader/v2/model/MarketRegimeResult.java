package com.smarttrader.v2.model;

import lombok.Builder;

/**
 * Output of MarketRegimeDetector, per V2_TECH_SPEC_v1.1.md section 2:
 * every detection returns the classified regime plus a confidence in [0, 1].
 */
@Builder
public record MarketRegimeResult(MarketRegime regime, double confidence) {
}
