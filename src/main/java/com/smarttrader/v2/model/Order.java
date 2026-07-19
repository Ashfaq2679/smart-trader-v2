package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

/**
 * A market order OrderService intended (dry-run) or placed (live), per the rebuilt
 * execution layer. Every TradeEngine-approved decision produces exactly one Order,
 * whether or not it actually reached Coinbase - dryRun/status distinguish "logged only"
 * from "really sent," so this collection is a complete record of every decision the
 * system acted (or tried to act) on.
 */
@Data
@Builder
@Document("orders")
public class Order {

    @Id
    private String id;

    @Indexed
    private String symbol;

    /** "BUY" or "SELL" (Coinbase's order side vocabulary, not TradeDirection). */
    private String side;

    /** Always "MARKET" today; kept as a field for when LIMIT support is added. */
    private String orderType;

    /** Base-currency quantity, taken directly from TradeDecision.positionSize(). */
    private double baseSize;

    private String clientOrderId;

    private String coinbaseOrderId;

    private OrderStatus status;

    private boolean dryRun;

    private String strategyName;

    private MarketRegime regime;

    private Double filledSize;

    private Double averageFilledPrice;

    private String failureReason;

    private long createdAtNs;

    @Indexed
    private Instant createdAt;
}
