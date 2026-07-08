package com.smarttrader.v2.risk;

import com.smarttrader.v2.model.MarketRegime;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.portfolio.PortfolioRiskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalRiskCheckTest {

    @Mock
    private PortfolioRiskService portfolioRiskService;

    private GlobalRiskCheck globalRiskCheck;

    @BeforeEach
    void setUp() {
        globalRiskCheck = new GlobalRiskCheck(portfolioRiskService);
    }

    @Test
    void delegatesDirectlyToPortfolioRiskService() {
        TradeDecision decision = TradeDecision.rejected(MarketRegime.PULLBACK, SignalResult.invalid("PullbackStrategy"), "n/a");
        TradeDecision adjusted = decision.toBuilder().reason("adjusted").build();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        when(portfolioRiskService.apply(decision, "BTC-USD", 10_000, "corr-1", now)).thenReturn(adjusted);

        TradeDecision result = globalRiskCheck.apply(decision, "BTC-USD", 10_000, "corr-1", now);

        assertThat(result).isEqualTo(adjusted);
    }
}
