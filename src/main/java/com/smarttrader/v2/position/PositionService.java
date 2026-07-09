package com.smarttrader.v2.position;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.event.DomainEventPublisher;
import com.smarttrader.v2.event.OrderFilledEvent;
import com.smarttrader.v2.event.PositionClosedEvent;
import com.smarttrader.v2.event.PositionOpenedEvent;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Position Service Enhancements, per V2_TECH_SPEC_v1.1.md section 7:
 * - Unrealized loss guard (1.5x risk -> force exit)
 * - Partial fills supported
 * - State transitions enforced
 *
 * Also publishes the section 9 lifecycle events (PositionOpened, OrderFilled, PositionClosed),
 * and persists to MongoDB so positions can be rebuilt after a restart (section 11 "Recovery
 * & Replay" - "Rebuild positions from DB").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    /** Tolerance for double comparisons when checking fill completeness. */
    private static final double SIZE_EPSILON = 1e-9;

    private final DomainEventPublisher eventPublisher;
    private final PositionRepository positionRepository;

    private final Map<String, Position> positions = new ConcurrentHashMap<>();

    /**
     * Rebuilds in-memory position state from MongoDB on startup, per section 11
     * "Rebuild positions from DB".
     */
    @PostConstruct
    void rebuildFromDatabase() {
    	log.info("positionService rebuilding positions from database");
        List<PositionDocument> documents = positionRepository.findAll();
        documents.forEach(document -> positions.put(document.getPositionId(), fromDocument(document)));
        log.info("positionService rebuilt {} positions from database", documents.size());
    }

    /** Convenience overload: generates a fresh correlationId. */
    public Position open(TradeDecision decision, String productId, String positionId, Instant now) {
        return open(decision, productId, positionId, now, UUID.randomUUID().toString());
    }

    /**
     * Opens a new PENDING position from an approved TradeDecision (the same decision and
     * productId used to place the order via OrderExecutionService).
     *
     * @throws IllegalArgumentException if the decision was not approved, or positionId is blank
     * @throws IllegalStateException    if positionId is already in use
     */
    public Position open(TradeDecision decision, String productId, String positionId, Instant now, String correlationId) {
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
        positionRepository.save(toDocument(result));
        eventPublisher.publish(PositionOpenedEvent.of(correlationId, result));
        return result;
    }

    /** Convenience overload: generates a fresh correlationId. */
    public Position recordFill(String positionId, double fillQuantity, Instant now) {
        return recordFill(positionId, fillQuantity, now, UUID.randomUUID().toString());
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
    public Position recordFill(String positionId, double fillQuantity, Instant now, String correlationId) {
        if (fillQuantity <= 0) {
            throw new IllegalArgumentException("fillQuantity must be positive");
        }
        Position updated = positions.compute(positionId, (id, current) -> {
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

            Position next = current.toBuilder().filledSize(newFilled).status(target).build();
            log.info("position fill positionId={} fillQuantity={} filledSize={} status={}",
                    positionId, fillQuantity, newFilled, target);
            return next;
        });
        positionRepository.save(toDocument(updated));
        eventPublisher.publish(OrderFilledEvent.of(correlationId, positionId, fillQuantity, updated.filledSize(), now));
        return updated;
    }

    /** Convenience overload: generates a fresh correlationId. */
    public Position evaluateUnrealizedLossGuard(String positionId, double currentPrice, Instant now) {
        return evaluateUnrealizedLossGuard(positionId, currentPrice, now, UUID.randomUUID().toString());
    }

    /**
     * Force-closes the position if its unrealized loss has reached
     * UNREALIZED_LOSS_GUARD_RISK_MULTIPLIER times its original per-unit risk. No-op if the
     * position has no fills yet or is already closed.
     *
     * @throws IllegalArgumentException if there's no such position
     */
    public Position evaluateUnrealizedLossGuard(String positionId, double currentPrice, Instant now, String correlationId) {
        Position result = positions.compute(positionId, (id, current) -> {
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

        if (result.status() == PositionStatus.CLOSED && now.equals(result.closedAt())) {
            positionRepository.save(toDocument(result));
            eventPublisher.publish(PositionClosedEvent.of(correlationId, result));
        }
        return result;
    }

    /** Convenience overload: generates a fresh correlationId. */
    public Position close(String positionId, String reason, Instant now) {
        return close(positionId, reason, now, UUID.randomUUID().toString());
    }

    /**
     * Explicitly closes a position (e.g. target/stop hit, manual exit).
     *
     * @throws IllegalArgumentException if there's no such position
     * @throws IllegalStateException    if the position is already CLOSED
     */
    public Position close(String positionId, String reason, Instant now, String correlationId) {
        Position result = positions.compute(positionId, (id, current) -> {
            requireExists(current, positionId);
            requireTransition(current.status(), PositionStatus.CLOSED, positionId);
            log.info("position closed positionId={} reason={}", positionId, reason);
            return current.toBuilder().status(PositionStatus.CLOSED).closedAt(now).closeReason(reason).build();
        });
        positionRepository.save(toDocument(result));
        eventPublisher.publish(PositionClosedEvent.of(correlationId, result));
        return result;
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

    private static PositionDocument toDocument(Position position) {
        PositionDocument document = new PositionDocument();
        document.setPositionId(position.positionId());
        document.setProductId(position.productId());
        document.setDirection(position.direction() == null ? null : position.direction().name());
        document.setEntryPrice(position.entryPrice());
        document.setStopPrice(position.stopPrice());
        document.setTargetPrice(position.targetPrice());
        document.setRequestedSize(position.requestedSize());
        document.setFilledSize(position.filledSize());
        document.setStatus(position.status() == null ? null : position.status().name());
        document.setOpenedAt(position.openedAt());
        document.setClosedAt(position.closedAt());
        document.setCloseReason(position.closeReason());
        return document;
    }

    private static Position fromDocument(PositionDocument document) {
        return Position.builder()
                .positionId(document.getPositionId())
                .productId(document.getProductId())
                .direction(document.getDirection() == null ? null : TradeDirection.valueOf(document.getDirection()))
                .entryPrice(document.getEntryPrice())
                .stopPrice(document.getStopPrice())
                .targetPrice(document.getTargetPrice())
                .requestedSize(document.getRequestedSize())
                .filledSize(document.getFilledSize())
                .status(document.getStatus() == null ? null : PositionStatus.valueOf(document.getStatus()))
                .openedAt(document.getOpenedAt())
                .closedAt(document.getClosedAt())
                .closeReason(document.getCloseReason())
                .build();
    }
}
