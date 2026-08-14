package com.smarttrader.v2.event;

public class PositionClosedEvent extends TradingEvent {

    public String positionId;
    public double exitPrice;
    public double realizedPnl;

    public PositionClosedEvent() {
        super("execution.PositionClosed");
    }
}
