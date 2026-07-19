package com.smarttrader.v2.event;

public class PositionOpenedEvent extends TradingEvent {

    public String positionId;
    public double entryPrice;
    public double quantity;

    public PositionOpenedEvent() {
        super("execution.PositionOpened");
    }
}
