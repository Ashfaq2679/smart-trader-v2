package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * Persisted audit record of a TradeDecision (V2_TECH_SPEC_v1.1.md section 5 output),
 * for history/replay. Named distinctly from com.smarttrader.v2.model.TradeDecision
 * (the in-flight decision-pipeline record) to avoid confusion between the two: this is
 * the durable, flattened snapshot written after a decision is made.
 */
@Data
@Document("trade_decisions")
public class TradeDecisionDocument {

	@Id
	private String id;

	@Indexed
	private String productId;

	private String regime;

	private double regimeConfidence;

	private boolean approved;

	private double effectiveRiskReward;

	private double positionSize;

	private String reason;

	@Indexed
	private Instant timestamp;
}
