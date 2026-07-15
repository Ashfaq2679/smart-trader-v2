package com.smarttrader.v2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

/**
 * Risk-budget multiplier for a FULL-stage (strategy, symbol) pair, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5.3 (MetaAllocator). Persisted for
 * audit/inspection; not yet consumed by RiskEngine/PositionSizing (RiskEngine is a
 * pinned file - see MetaAllocator's javadoc for why that wiring is deferred).
 */
@Data
@Builder
@Document("strategy_risk_budgets")
public class StrategyRiskBudget {

    @Id
    private String id;

    @Indexed
    private String strategyName;

    @Indexed
    private String symbol;

    private double multiplier;

    private double rollingExpectancy;

    private long updatedAtNs;
}
