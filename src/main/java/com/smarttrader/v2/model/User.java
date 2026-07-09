package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document for a trading account, per CLAUDE.md's "users" collection.
 * userId is the @Id field: UserRepository's built-in findById/existsById/deleteById
 * operate directly on it.
 */
@Data
@Document("users")
public class User {

	@Id
	private String userId;

	@Indexed(unique = true)
	private String userName;

	/** Available USD funds for sizing/validating new orders. */
	private double currentFunds;

	private Instant createdAt;

	private Instant updatedAt;
}
