package com.smarttrader.v2.portfolio;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.event.DomainEventPublisher;
import com.smarttrader.v2.event.PortfolioUpdatedEvent;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.position.Position;
import com.smarttrader.v2.position.PositionService;
import com.smarttrader.v2.position.PositionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Portfolio Risk Controls, per V2_TECH_SPEC_v1.1.md section 8:
 * - Dynamic correlation matrix (rolling) -> CorrelationTracker
 * - Exposure auto-adjustment -> caps total open notional exposure at
 *   TradingConstants.MAX_PORTFOLIO_EXPOSURE_PERCENT of capital
 * - Adaptive position sizing -> shrinks the proposed size when it's highly correlated
 *   with already-open positions, so concentrated/correlated risk isn't sized as if it
 *   were independent
 *
 * This is what backs GlobalRiskCheck (decision flow step 6) once portfolio state exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioRiskService {

    private final PositionService positionService;
    private final CorrelationTracker correlationTracker;
    private final DomainEventPublisher eventPublisher;

    public TradeDecision apply(TradeDecision decision, String productId, double capital, String correlationId, Instant now) {
        List<Position> openPositions = openPositions();
        double currentExposure = totalExposure(openPositions);

        eventPublisher.publish(PortfolioUpdatedEvent.of(correlationId, currentExposure, openPositions.size(), now));

        if (!decision.approved()) {
            return decision;
        }

        double maxExposure = capital * TradingConstants.MAX_PORTFOLIO_EXPOSURE_PERCENT;
        double availableExposure = maxExposure - currentExposure;
        if (availableExposure <= 0) {
            return reject(decision, "portfolio exposure limit reached: current=%.2f max=%.2f"
                    .formatted(currentExposure, maxExposure));
        }

        double entry = decision.signal().entry();
        double correlationMultiplier = correlationMultiplier(productId, openPositions);
        double correlationAdjustedSize = decision.positionSize() * correlationMultiplier;
        double exposureCappedSize = availableExposure / entry;
        double finalSize = Math.min(correlationAdjustedSize, exposureCappedSize);

        if (finalSize <= 0) {
            return reject(decision, "adaptive position size reduced to zero by portfolio risk controls");
        }

        if (finalSize >= decision.positionSize()) {
            return decision;
        }

        log.info("portfolioRisk productId={} correlationMultiplier={} originalSize={} adjustedSize={}",
                productId, correlationMultiplier, decision.positionSize(), finalSize);
        return decision.toBuilder()
                .positionSize(finalSize)
                .reason(decision.reason() + "; size adjusted by portfolio risk controls (correlationMultiplier=%.2f)"
                        .formatted(correlationMultiplier))
                .build();
    }

    private List<Position> openPositions() {
        return positionService.findAll().stream()
                .filter(position -> position.status() != PositionStatus.CLOSED)
                .toList();
    }

    private double totalExposure(Collection<Position> openPositions) {
        return openPositions.stream()
                .mapToDouble(position -> position.requestedSize() * position.entryPrice())
                .sum();
    }

    private double correlationMultiplier(String productId, List<Position> openPositions) {
        Set<String> otherProducts = openPositions.stream()
                .map(Position::productId)
                .collect(Collectors.toSet());

        double multiplier = 1.0;
        for (String otherProductId : otherProducts) {
            Optional<Double> correlation = correlationTracker.correlation(productId, otherProductId);
            if (correlation.isPresent() && Math.abs(correlation.get()) > TradingConstants.CORRELATION_THRESHOLD) {
                multiplier *= TradingConstants.CORRELATION_SIZE_REDUCTION_FACTOR;
            }
        }
        return Math.max(multiplier, TradingConstants.MIN_CORRELATION_MULTIPLIER);
    }

    private TradeDecision reject(TradeDecision decision, String reason) {
        log.info("portfolioRisk rejected reason={}", reason);
        return decision.toBuilder()
                .approved(false)
                .positionSize(0)
                .reason(reason)
                .build();
    }
}
