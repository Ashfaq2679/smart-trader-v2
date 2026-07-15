package com.smarttrader.v2.model;

import java.time.Instant;

import lombok.Builder;

/**
 * A single trade print from the exchange matches (tick) stream, per
 * V2_TECH_SPEC_v2.5.md section 4/9 ("Matches-stream ingestion mandatory - CVD is built
 * from it"). WebSocket ingestion into CoinbaseClientImpl isn't built yet (Phase 1B.1
 * only consumes whatever is handed to CVDCalculatorService.onMatchesStream); this is the
 * shape that ingestion will eventually produce.
 */
@Builder
public record Match(
        String symbol,
        /** "BUY" or "SELL": which side was the taker (aggressor) in this print. */
        String takerSide,
        double volume,
        Instant timestamp
) {
}
