package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document for a user's trading preferences, per CLAUDE.md's "preferences"
 * collection.
 */
@Data
@Document("preferences")
public class UserPreferences {

	@Id
	private String id;

	@Indexed(unique = true)
	private String userId;

	/** Overrides RiskEngine.DEFAULT_RISK_PERCENT when set. */
	private Double riskPercent;

	/** Overrides TradingConstants.MAX_PORTFOLIO_EXPOSURE_PERCENT when set. */
	private Double maxPortfolioExposurePercent;

	private Instant createdAt;

	private Instant updatedAt;
}
