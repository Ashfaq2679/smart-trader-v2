package com.smarttrader.v2.position;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document for a Position, per CLAUDE.md's "positions" collection and
 * V2_TECH_SPEC_v1.1.md section 11 ("Rebuild positions from DB"). Kept as a separate
 * persistence shape from the position.Position record (mirrors the TradeDecision /
 * TradeDecisionDocument split) so the in-memory domain type isn't coupled to Mongo
 * mapping annotations.
 */
@Data
@Document("positions")
public class PositionDocument {

	@Id
	private String positionId;

	@Indexed
	private String productId;

	private String direction;

	private double entryPrice;

	private double stopPrice;

	private double targetPrice;

	private double requestedSize;

	private double filledSize;

	@Indexed
	private String status;

	private Instant openedAt;

	private Instant closedAt;

	private String closeReason;
}
