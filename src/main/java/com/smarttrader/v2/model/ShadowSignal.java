package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

/**
 * A signal a RESEARCH/SHADOW-stage strategy produced, logged without placing an order,
 * per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.5. Flattened SignalResult fields
 * (not an embedded SignalResult) for the same reason Opportunity flattens its context
 * snapshot: stable Mongo shape independent of the pipeline record's evolution.
 */
@Data
@Builder
@Document("shadow_signals")
public class ShadowSignal {

    @Id
    private String id;

    @Indexed
    private String strategyName;

    @Indexed
    private String symbol;

    private String direction;

    private double entry;

    private double stop;

    private double target;

    private double riskReward;

    private boolean valid;

    private long detectedAtNs;

    @Indexed
    private Instant detectedAt;
}
