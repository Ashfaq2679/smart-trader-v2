package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

/**
 * A tracked spot position opened by a filled BUY Order and closed by a later filled SELL
 * Order, per PositionService (execution-layer rebuild). Spot-only: this codebase's
 * venue.can-short default is false, so "side" is expected to always be LONG today, but
 * the field stays a plain String rather than hardcoding that assumption.
 *
 * PositionService tracks lifecycle from order fills only - it does not itself watch live
 * price against stopPrice/targetPrice to fire an exit order. That's a natural next
 * increment (a monitoring loop), not something silently claimed as done here.
 */
@Data
@Builder
@Document("positions")
public class Position {

    @Id
    private String id;

    @Indexed
    private String symbol;

    private String side;

    private double entryPrice;

    private double quantity;

    private double stopPrice;

    private double targetPrice;

    private PositionStatus status;

    private String openOrderId;

    private String closeOrderId;

    private Double realizedPnl;

    private long openedAtNs;

    @Indexed
    private Instant openedAt;

    private long closedAtNs;

    private Instant closedAt;
}
