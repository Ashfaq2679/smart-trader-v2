package com.smarttrader.v2.position;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Position lifecycle states, per V2_TECH_SPEC_v1.1.md section 7 ("State transitions
 * enforced" and "Partial fills supported").
 *
 *   PENDING -> PARTIALLY_FILLED -> OPEN -> CLOSED
 *   PENDING -> OPEN (fills completely in one shot)
 *   PENDING/PARTIALLY_FILLED -> CLOSED (closed before fully filled)
 *
 * CLOSED is terminal: no further transitions are allowed out of it.
 */
public enum PositionStatus {
    PENDING,
    PARTIALLY_FILLED,
    OPEN,
    CLOSED;

    private static final Map<PositionStatus, Set<PositionStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(PositionStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(PARTIALLY_FILLED, OPEN, CLOSED));
        ALLOWED_TRANSITIONS.put(PARTIALLY_FILLED, EnumSet.of(PARTIALLY_FILLED, OPEN, CLOSED));
        ALLOWED_TRANSITIONS.put(OPEN, EnumSet.of(CLOSED));
        ALLOWED_TRANSITIONS.put(CLOSED, EnumSet.noneOf(PositionStatus.class));
    }

    public boolean canTransitionTo(PositionStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}
