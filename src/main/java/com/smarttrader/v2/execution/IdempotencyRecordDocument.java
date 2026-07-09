package com.smarttrader.v2.execution;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document for a durable idempotency record, so a restart doesn't lose the
 * "already processed this order submission" guard (V2_TECH_SPEC_v1.1.md section 11,
 * "Resume without duplicate orders").
 */
@Data
@Document("idempotency_records")
public class IdempotencyRecordDocument {

	@Id
	private String idempotencyKey;

	private String productId;

	private String status;

	private String reason;

	private String direction;

	private double requestedPrice;

	private double quotedPrice;

	private double slippage;

	private double positionSize;

	private Instant evaluatedAt;
}
