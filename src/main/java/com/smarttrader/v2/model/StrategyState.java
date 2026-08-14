package com.smarttrader.v2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

/**
 * Tracked validation-pipeline state for a (strategyName, symbol) pair, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.2. Mutable/tracked, like LiquidityPool,
 * not an immutable pipeline record - stage changes over the strategy's lifetime.
 */
@Data
@Builder
@Document("strategy_states")
public class StrategyState {

    @Id
    private String id;

    @Indexed
    private String strategyName;

    @Indexed
    private String symbol;

    private StrategyStage stage;

    private long lastPromotedNs;

    private String lastReason;
}
