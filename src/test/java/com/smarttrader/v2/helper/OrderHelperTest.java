package com.smarttrader.v2.helper;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.model.orders.OrderConfiguration;
import com.coinbase.advanced.orders.OrdersService;
import com.smarttrader.v2.model.Order;
import com.smarttrader.v2.model.OrderRequest;
import com.smarttrader.v2.repository.OrderRepository;
import com.smarttrader.v2.service.ClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.smarttrader.v2.constants.OrderConstants.MAX_USD_PER_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderHelperTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ClientService clientService;
    @Mock
    private CoinbaseAdvancedClient coinbaseAdvancedClient;

    // --- buildOrderConfiguration ---

    @Test
    void bullish_marketOrderWithBaseSizeUsesBaseSize() {
        OrderRequest request = OrderRequest.builder().productId("BTC-USD").side("BUY")
                .orderType("MARKET").baseSize(0.01).build();

        OrderConfiguration config = OrderHelper.buildOrderConfiguration(request, orderRepository);

        assertThat(config.getMarketMarketIoc().getBaseSize()).isEqualTo("0.01");
    }

    @Test
    void bullish_marketOrderWithoutBaseSizeUsesQuoteSize() {
        OrderRequest request = OrderRequest.builder().productId("BTC-USD").side("BUY")
                .orderType("MARKET").quoteSize(25.0).build();

        OrderConfiguration config = OrderHelper.buildOrderConfiguration(request, orderRepository);

        assertThat(config.getMarketMarketIoc().getQuoteSize()).isEqualTo("25.0");
    }

    @Test
    void bullish_bracketOrderWhenStopLossAndTakeProfitBothSet() {
        OrderRequest request = OrderRequest.builder().productId("BTC-USD").side("BUY")
                .orderType("LIMIT").baseSize(0.01).limitPrice(100.0)
                .stopLoss(90.0).takeProfit(110.0).build();

        OrderConfiguration config = OrderHelper.buildOrderConfiguration(request, orderRepository);

        assertThat(config.getTriggerBracketGtc()).isNotNull();
        assertThat(config.getTriggerBracketGtc().getStopTriggerPrice()).isEqualTo("90.00");
        assertThat(config.getTriggerBracketGtc().getLimitPrice()).isEqualTo("110.00");
    }

    @Test
    void bullish_limitBuyOrderCapsNotionalAtMaxUsdPerOrder() {
        // requested notional = 10 * 100 = $1000, way over the $50 cap
        OrderRequest request = OrderRequest.builder().productId("BTC-USD").side("BUY")
                .orderType("LIMIT").baseSize(10.0).limitPrice(100.0).build();

        OrderConfiguration config = OrderHelper.buildOrderConfiguration(request, orderRepository);

        double baseSize = Double.parseDouble(config.getLimitLimitGtc().getBaseSize());
        double notional = baseSize * 100.0;
        assertThat(notional).isLessThanOrEqualTo(MAX_USD_PER_ORDER + 0.01);
        // buy price is nudged down slightly (100 - 0.1%)
        assertThat(config.getLimitLimitGtc().getLimitPrice()).isEqualTo("99.90");
    }

    @Test
    void bullish_limitBuyOrderWithinCapKeepsRequestedBaseSize() {
        // requested notional = 0.1 * 100 = $10, within the $50 cap
        OrderRequest request = OrderRequest.builder().productId("BTC-USD").side("BUY")
                .orderType("LIMIT").baseSize(0.1).limitPrice(100.0).build();

        OrderConfiguration config = OrderHelper.buildOrderConfiguration(request, orderRepository);

        assertThat(Double.parseDouble(config.getLimitLimitGtc().getBaseSize())).isEqualTo(0.1);
    }

    @Test
    void bearish_limitSellOrderIsCappedAtAvailableQuantity() {
        Order existingBuy = new Order();
        existingBuy.setSide("BUY");
        existingBuy.setQty(1.0);
        when(orderRepository.findByProductId("BTC-USD")).thenReturn(List.of(existingBuy));

        OrderRequest request = OrderRequest.builder().productId("BTC-USD").side("SELL")
                .orderType("LIMIT").baseSize(5.0).limitPrice(100.0).build();

        OrderConfiguration config = OrderHelper.buildOrderConfiguration(request, orderRepository);

        assertThat(config.getLimitLimitGtc().getBaseSize()).isEqualTo("1.0");
        // sell price is nudged up slightly (100 + 0.5%)
        assertThat(config.getLimitLimitGtc().getLimitPrice()).isEqualTo("100.50");
    }

    // --- getQtyBySideFromCache ---

    @Test
    void getQtyBySideFromCacheSumsBuysAndSellsSeparately() {
        Order buy1 = new Order();
        buy1.setSide("BUY");
        buy1.setQty(2.0);
        Order buy2 = new Order();
        buy2.setSide("buy");
        buy2.setQty(1.0);
        Order sell = new Order();
        sell.setSide("SELL");
        sell.setQty(0.5);
        when(orderRepository.findByProductId("BTC-USD")).thenReturn(List.of(buy1, buy2, sell));

        Map<String, Double> result = OrderHelper.getQtyBySideFromCache(orderRepository, "BTC-USD");

        assertThat(result.get("BUY")).isEqualTo(3.0);
        assertThat(result.get("SELL")).isEqualTo(0.5);
    }

    // --- getOrderServiceFromCache ---

    @Test
    void edgeCase_getOrderServiceFromCacheReturnsNullWhenNoClientForUser() {
        when(clientService.getCoinbaseClientForUserFromCache("missing-user")).thenReturn(null);

        OrdersService result = OrderHelper.getOrderServiceFromCache(clientService, "missing-user", new ConcurrentHashMap<>());

        assertThat(result).isNull();
    }

    @Test
    void edgeCase_getOrderServiceFromCacheReusesCachedInstanceForSameClient() {
        when(clientService.getCoinbaseClientForUserFromCache("user-1")).thenReturn(coinbaseAdvancedClient);
        Map<CoinbaseAdvancedClient, OrdersService> cache = new ConcurrentHashMap<>();

        OrdersService first = OrderHelper.getOrderServiceFromCache(clientService, "user-1", cache);
        OrdersService second = OrderHelper.getOrderServiceFromCache(clientService, "user-1", cache);

        assertThat(first).isNotNull();
        assertThat(second).isSameAs(first);
    }
}
