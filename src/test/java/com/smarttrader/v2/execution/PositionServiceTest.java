package com.smarttrader.v2.execution;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.event.PositionOpenedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.Order;
import com.smarttrader.v2.model.OrderStatus;
import com.smarttrader.v2.model.Position;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TradingEventPublisher eventPublisher;

    private PositionService service() {
        return new PositionService(positionRepository, eventPublisher);
    }

    private Order order(OrderStatus status, String side) {
        return Order.builder()
                .id("order-1").symbol("BTC-USD").side(side).orderType("MARKET")
                .baseSize(1.5).clientOrderId("c1").status(status).dryRun(status == OrderStatus.DRY_RUN)
                .strategyName("PullbackStrategy").regime(MarketRegime.PULLBACK)
                .createdAtNs(1L).createdAt(LocalDateTime.now(ZoneId.of("America/New_York"))).build();
    }

    private SignalResult signal(TradeDirection direction) {
        return SignalResult.builder().valid(true).strategyName("PullbackStrategy")
                .direction(direction).entry(100.0).stop(95.0).target(110.0).riskReward(2.0).build();
    }

    @Test
    void bullish_placedBuyOrderOpensALongPosition() {
        PositionService service = service();
        when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Position> result = service.onOrderPlaced(order(OrderStatus.PLACED, "BUY"), signal(TradeDirection.LONG));

        assertThat(result).isPresent();
        assertThat(result.get().getSide()).isEqualTo("LONG");
        assertThat(result.get().getEntryPrice()).isEqualTo(100.0);
        assertThat(result.get().getQuantity()).isEqualTo(1.5);
        assertThat(result.get().getStopPrice()).isEqualTo(95.0);
        assertThat(result.get().getTargetPrice()).isEqualTo(110.0);

        ArgumentCaptor<PositionOpenedEvent> captor = ArgumentCaptor.forClass(PositionOpenedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().entryPrice).isEqualTo(100.0);
    }

    @Test
    void bearish_placedSellOrderOpensAShortPosition() {
        PositionService service = service();
        when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Position> result = service.onOrderPlaced(order(OrderStatus.PLACED, "SELL"), signal(TradeDirection.SHORT));

        assertThat(result).isPresent();
        assertThat(result.get().getSide()).isEqualTo("SHORT");
    }

    @Test
    void sideways_dryRunOrderNeverOpensAPosition() {
        PositionService service = service();

        Optional<Position> result = service.onOrderPlaced(order(OrderStatus.DRY_RUN, "BUY"), signal(TradeDirection.LONG));

        assertThat(result).isEmpty();
        verifyNoInteractions(positionRepository, eventPublisher);
    }

    @Test
    void edgeCase_failedOrderNeverOpensAPosition() {
        PositionService service = service();

        Optional<Position> result = service.onOrderPlaced(order(OrderStatus.FAILED, "BUY"), signal(TradeDirection.LONG));

        assertThat(result).isEmpty();
        verifyNoInteractions(positionRepository, eventPublisher);
    }
}
