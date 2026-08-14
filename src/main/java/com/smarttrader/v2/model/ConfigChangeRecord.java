package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

/**
 * Audit trail entry for a Phase 5 feedback-loop config change, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5.4 ("ConfigChanged events: persisted
 * with full audit trail, reversible"). Reversibility here means oldValue is always
 * retained so a human can manually restore it - there is no automatic rollback.
 */
@Data
@Builder
@Document("config_change_records")
public class ConfigChangeRecord {

    @Id
    private String id;

    @Indexed
    private String configKey;

    private String symbol;

    private String oldValue;

    private String newValue;

    private String reason;

    private long changedAtNs;

    @Indexed
    private Instant changedAt;
}
