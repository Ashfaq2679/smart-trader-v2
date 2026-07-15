package com.smarttrader.v2.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

/**
 * A siren-worthy opportunity, per V2_TECH_SPEC_v2.5.md section 7: "The system must never
 * see an opportunity and whisper" - every playbook-cell activation persists here,
 * executable or not, at full severity, so a non-executable short on a long-only venue
 * still leaves a record of "the exact thesis and levels."
 *
 * contextSnapshot is a flattened Map (not the raw AnalysisContext) to keep this document
 * stable as AnalysisContext's shape evolves, and to avoid coupling Mongo's serialization
 * to an internal pipeline record.
 *
 * wouldHaveR1h/4h/24h stay null until OpportunityScoringJob (Phase 3.3) scores them.
 */
@Data
@Builder
@Document("opportunities")
public class Opportunity {

    @Id
    private String id;

    @Indexed
    private String symbol;

    private String playbook;

    private String direction;

    private Severity severity;

    private boolean executable;

    private String reason;

    private double entry;

    private double stop;

    private double target;

    private Map<String, Object> contextSnapshot;

    private long createdAtNs;

    /** TTL index: MongoDB expires the document 180 days after createdAt (section 7). */
    @Indexed(name = "opportunity_ttl_idx", expireAfterSeconds = 0)
    private Instant expiresAt;

    private boolean scored;

    private Float wouldHaveR1h;

    private Float wouldHaveR4h;

    private Float wouldHaveR24h;
}
