package com.smarttrader.v2.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Produces a stable, deterministic ID from a set of business-key parts, so the same
 * logical occurrence always yields the same ID. Used as DomainEvent.eventId() so that
 * republishing the same event (e.g. on retry) is safe to dedupe by ID, per
 * V2_TECH_SPEC_v1.1.md section 9's "each event must be idempotent" requirement.
 */
final class DeterministicId {

    private DeterministicId() {
    }

    static String from(Object... parts) {
        StringBuilder key = new StringBuilder();
        for (Object part : parts) {
            key.append(part).append('|');
        }
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }
}
