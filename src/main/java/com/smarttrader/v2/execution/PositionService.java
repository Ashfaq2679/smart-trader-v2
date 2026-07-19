package com.smarttrader.v2.execution;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.constants.OrderConstants;
import com.smarttrader.v2.event.PositionOpenedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.Order;
import com.smarttrader.v2.model.OrderStatus;
import com.smarttrader.v2.model.Position;
import com.smarttrader.v2.model.PositionStatus;
import com.smarttrader.v2.model.SignalResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Opens a Position when OrderService places a real (status=PLACED) order, per the
 * rebuilt execution layer. Dry-run orders never open a position - there's nothing real
 * to track - and Coinbase's spot venue.can-short=false default means a BUY order opens a
 * LONG and a SELL order (only reachable when a venue allows shorting - see
 * ShortSideStrategy/RangeHarvesterStrategy) opens a SHORT; today's strategy layer has no
 * distinct "close this position" signal type, only fresh entries, so this intentionally
 * does not try to match a SELL against an existing LONG to "close" it.
 *
 * This does not watch live price against a position's stored stop/target to close it
 * automatically or realize P&amp;L - that needs a price-monitoring loop this codebase
 * doesn't have yet. Documented as a gap, not silently implied as covered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;
    private final TradingEventPublisher eventPublisher;

    public Optional<Position> onOrderPlaced(Order order, SignalResult signal) {
        if (order.getStatus() != OrderStatus.PLACED) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        Position position = Position.builder()
                .symbol(order.getSymbol())
                .side(OrderConstants.SIDE_BUY.equals(order.getSide()) ? "LONG" : "SHORT")
                .entryPrice(signal.entry())
                .quantity(order.getBaseSize())
                .stopPrice(signal.stop())
                .targetPrice(signal.target())
                .status(PositionStatus.OPEN)
                .openOrderId(order.getId())
                .openedAtNs(System.nanoTime())
                .openedAt(now)
                .build();

        position = positionRepository.save(position);

        PositionOpenedEvent event = new PositionOpenedEvent();
        event.symbol = position.getSymbol();
        event.positionId = position.getId();
        event.entryPrice = position.getEntryPrice();
        event.quantity = position.getQuantity();
        eventPublisher.publish(event);

        log.info("positionService opened symbol={} side={} entry={} qty={} stop={} target={}",
                position.getSymbol(), position.getSide(), position.getEntryPrice(),
                position.getQuantity(), position.getStopPrice(), position.getTargetPrice());
        return Optional.of(position);
    }
}
