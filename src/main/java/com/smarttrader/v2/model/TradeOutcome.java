package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

/**
 * A closed trade's realized outcome for a (strategy, symbol) pair, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.4 (StrategyDemotionMonitor) / 4.3
 * (ValidationPipelineService.validateMicroLive).
 *
 * This is new, minimal infra: this codebase has no order-execution/fill persistence yet
 * (no OrderService/PositionService, confirmed absent from this v2.5 baseline - see
 * DefensiveActionTakenEvent's javadoc for the same finding). Demotion/promotion decisions
 * need *some* real record of realized results to gate on rather than fabricated numbers,
 * so this document exists to be written by whatever future execution layer closes a
 * trade; until that exists, TradeOutcomeRepository is simply empty and every consumer
 * here treats "no data" as "not enough evidence to act," never as "assume it's fine."
 */
@Data
@Builder
@Document("trade_outcomes")
public class TradeOutcome {

    @Id
    private String id;

    @Indexed
    private String strategyName;

    @Indexed
    private String symbol;

    /** Realized profit/loss expressed in units of the trade's own risk (R). */
    private double realizedR;

    /** Actual fill slippage divided by the modeled/expected slippage; 1.0 = as modeled. */
    private double slippageMultiple;

    /** True if realizedR > 0. */
    private boolean win;

    private long closedAtNs;

    @Indexed
    private Instant closedAt;
}
