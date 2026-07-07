package com.smarttrader.v2.execution;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory IdempotencyKeyStore. This is a placeholder: it does not survive a restart, so
 * it only protects against duplicate submissions within a single running instance. A
 * durable (e.g. MongoDB-backed) implementation belongs with the order persistence work
 * in section 7 (Position Service Enhancements), which is not yet implemented.
 */
@Component
public class InMemoryIdempotencyKeyStore implements IdempotencyKeyStore {

    private final Map<String, OrderResult> resultsByKey = new ConcurrentHashMap<>();

    @Override
    public Optional<OrderResult> find(String idempotencyKey) {
        return Optional.ofNullable(resultsByKey.get(idempotencyKey));
    }

    @Override
    public void save(String idempotencyKey, OrderResult result) {
        resultsByKey.put(idempotencyKey, result);
    }
}
