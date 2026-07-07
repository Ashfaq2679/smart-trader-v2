package com.smarttrader.v2.execution;

import java.util.Optional;

/**
 * Tracks idempotency keys already processed by OrderExecutionService, per
 * V2_TECH_SPEC_v1.1.md section 6 ("Idempotency keys required"): retrying an
 * order submission with the same key must return the original result rather
 * than re-executing it.
 */
public interface IdempotencyKeyStore {

    Optional<OrderResult> find(String idempotencyKey);

    void save(String idempotencyKey, OrderResult result);
}
