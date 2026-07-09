package com.smarttrader.v2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document for a tradable coin/product, per CLAUDE.md's "coins" collection.
 */
@Data
@Document("coins")
public class CoinDocument {

	@Id
	private String id;

	@Indexed(unique = true)
	private String productId;

	private String symbol;

	private String name;

	private boolean active;
}
