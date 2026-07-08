package com.smarttrader.v2.position;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Position Service Enhancements, per V2_TECH_SPEC_v1.1.md section 7:
 * - Unrealized loss guard (1.5x risk -> force exit)
 * - Partial fills supported
 * - State transitions enforced
 *
 * In-memory only: like OrderExecutionService's IdempotencyKeyStore, a durable
 * (MongoDB-backed) store belongs to the "positions" collection named in CLAUDE.md but
 * isn't implemented yet. This keeps the position lifecycle rules testable independent
 * of persistence.
 */
@Slf4j
@Service
public class PositionService {

    /** Tolerance for double comparisons when checking fill completeness. */
    private static final double SIZE_EPSILON = 1e-9;

    private final Map<String, Position> positions = new ConcurrentHashMap<>();

    /**
     * Opens a new PENDING position from an approved TradeDecision (the same decision and
     * productId used to place the order via OrderExecutionService).
     *
     * @throws IllegalArgumentException if the decision was not approved, or positionId is blank
     * @throws IllegalStateException    if positionId is already in use
     */
    public Position open(TradeDecision decision, String productId, String positionId, Instant now) {
        if (positionId == null || positionId.isBlank()) {
            throw new IllegalArgumentException("positionId is required");
        }
        if (!decision.approved()) {
            throw new IllegalArgumentException("cannot open a position from an unapproved TradeDecision");
        }

        SignalResult signal = decision.signal();
        Position created = Position.builder()
                .positionId(positionId)
                .productId(productId)
                .direction(signal.direction())
                .entryPrice(signal.entry())
                .stopPrice(signal.stop())
                .targetPrice(signal.target())
                .requestedSize(decision.positionSize())
                .filledSize(0)
                .status(PositionStatus.PENDING)
                .openedAt(now)
                .build();

        Position result = positions.merge(positionId, created, (existing, ignored) -> {
            throw new IllegalStateException("position already exists: " + positionId);
        });

        log.info("position opened positionId={} productId={} direction={} entry={} stop={} target={} size={}",
                positionId, productId, signal.direction(), signal.entry(), signal.stop(), signal.target(), decision.positionSize());
        return result;
    }

    /**
     * Records a (possibly partial) fill against a position, transitioning it to
     * PARTIALLY_FILLED or OPEN depending on how much of requestedSize is now filled.
     *
     * @throws IllegalArgumentException if there's no such position, fillQuantity isn't
     *                                   positive, or the fill would exceed the remaining size
     * @throws IllegalStateException    if the position's current status can't accept a fill
     *                                   (e.g. it's already CLOSED)
     */
    public Position recordFill(String positionId, double fillQuantity, Instant now) {
        if (fillQuantity <= 0) {
            throw new IllegalArgumentException("fillQuantity must be positive");
        }
        return positions.compute(positionId, (id, current) -> {
            requireExists(current, positionId);

            double newFilled = current.filledSize() + fillQuantity;
            if (newFilled > current.requestedSize() + SIZE_EPSILON) {
                throw new IllegalArgumentException("fill of %.8f exceeds remaining size %.8f for position %s"
                        .formatted(fillQuantity, current.remainingSize(), positionId));
            }

            PositionStatus target = newFilled >= current.requestedSize() - SIZE_EPSILON
                    ? PositionStatus.OPEN
                    : PositionStatus.PARTIALLY_FILLED;
            requireTransition(current.status(), target, positionId);

            Position updated = current.toBuilder().filledSize(newFilled).status(target).build();
            log.info("position fill positionId={} fillQuantity={} filledSize={} status={}",
                    positionId, fillQuantity, newFilled, target);
            return updated;
        });
    }

    /**
     * Force-closes the position if its unrealized loss has reached
     * UNREALIZED_LOSS_GUARD_RISK_MULTIPLIER times its original per-unit risk. No-op if the
     * position has no fills yet or is already closed.
     *
     * @throws IllegalArgumentException if there's no such position
     */
    public Position evaluateUnrealizedLossGuard(String positionId, double currentPrice, Instant now) {
        return positions.compute(positionId, (id, current) -> {
            requireExists(current, positionId);

            if (current.status() == PositionStatus.CLOSED || current.filledSize() <= 0) {
                return current;
            }

            double lossPerUnit = current.unrealizedLossPerUnit(currentPrice);
            double lossThreshold = current.riskPerUnit() * TradingConstants.UNREALIZED_LOSS_GUARD_RISK_MULTIPLIER;
            if (lossPerUnit <= lossThreshold) {
                return current;
            }

            requireTransition(current.status(), PositionStatus.CLOSED, positionId);
            String reason = "unrealized loss guard: loss/unit %.8f exceeds %.2fx risk (%.8f)"
                    .formatted(lossPerUnit, TradingConstants.UNREALIZED_LOSS_GUARD_RISK_MULTIPLIER, lossThreshold);
            log.warn("position forceClosed positionId={} reason={}", positionId, reason);
            return current.toBuilder().status(PositionStatus.CLOSED).closedAt(now).closeReason(reason).build();
        });
    }

    /**
     * Explicitly closes a position (e.g. target/stop hit, manual exit).
     *
     * @throws IllegalArgumentException if there's no such position
     * @throws IllegalStateException    if the position is already CLOSED
     */
    public Position close(String positionId, String reason, Instant now) {
        return positions.compute(positionId, (id, current) -> {
            requireExists(current, positionId);
            requireTransition(current.status(), PositionStatus.CLOSED, positionId);
            log.info("position closed positionId={} reason={}", positionId, reason);
            return current.toBuilder().status(PositionStatus.CLOSED).closedAt(now).closeReason(reason).build();
        });
    }

    public Optional<Position> find(String positionId) {
        return Optional.ofNullable(positions.get(positionId));
    }

    public Collection<Position> findAll() {
        return positions.values();
    }

    private void requireExists(Position current, String positionId) {
        if (current == null) {
            throw new IllegalArgumentException("no such position: " + positionId);
        }
    }

    private void requireTransition(PositionStatus from, PositionStatus to, String positionId) {
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException("illegal position state transition for %s: %s -> %s"
                    .formatted(positionId, from, to));
        }
    }
}
