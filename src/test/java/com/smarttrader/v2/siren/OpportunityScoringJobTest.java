package com.smarttrader.v2.siren;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.Opportunity;
import com.smarttrader.v2.service.ProductService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpportunityScoringJobTest {

    @Mock
    private OpportunityRepository opportunityRepository;

    @Mock
    private ProductService productService;

    private OpportunityScoringJob job() {
        return new OpportunityScoringJob(opportunityRepository, productService);
    }

    private Opportunity.OpportunityBuilder base() {
        return Opportunity.builder()
                .id("opp-1")
                .symbol("BTC-USD")
                .direction("LONG")
                .entry(100.0)
                .stop(95.0)
                .target(110.0)
                .scored(false);
    }

    private Candle candle(double close) {
        return Candle.builder().timestamp(Instant.now()).open(close).high(close).low(close).close(close).volume(1).build();
    }

    @Test
    void bullish_longSetupThatMovedInFavorProducesPositiveR() {
        Opportunity opportunity = base().build();
        when(opportunityRepository.findUnscoredOpportunities()).thenReturn(List.of(opportunity));
        List<Candle> candles = List.of(candle(100.0), candle(105.0), candle(107.0), candle(109.0),
                candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0),
                candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0),
                candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0),
                candle(110.0), candle(110.0), candle(115.0));
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(candles);

        job().scoreOpportunities();

        assertThat(opportunity.getWouldHaveR1h()).isEqualTo(1.0f);
        assertThat(opportunity.isScored()).isTrue();
        verify(opportunityRepository).save(opportunity);
    }

    @Test
    void bearish_shortSetupThatMovedAgainstProducesNegativeR() {
        Opportunity opportunity = base().direction("SHORT").entry(100.0).stop(105.0).build();
        when(opportunityRepository.findUnscoredOpportunities()).thenReturn(List.of(opportunity));
        List<Candle> candles = List.of(candle(100.0), candle(110.0), candle(110.0), candle(110.0),
                candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0),
                candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0),
                candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0), candle(110.0),
                candle(110.0), candle(110.0), candle(110.0));
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(candles);

        job().scoreOpportunities();

        assertThat(opportunity.getWouldHaveR1h()).isEqualTo(-2.0f);
    }

    @Test
    void sideways_alreadyScoredOpportunitiesAreNeverFetched() {
        when(opportunityRepository.findUnscoredOpportunities()).thenReturn(List.of());

        job().scoreOpportunities();

        verify(opportunityRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void edgeCase_notEnoughCandleHistoryLeavesHorizonUnscored() {
        Opportunity opportunity = base().build();
        when(opportunityRepository.findUnscoredOpportunities()).thenReturn(List.of(opportunity));
        when(productService.getLiveCandles("BTC-USD", Granularity.ONE_HOUR)).thenReturn(List.of(candle(100.0)));

        job().scoreOpportunities();

        assertThat(opportunity.getWouldHaveR1h()).isNull();
        assertThat(opportunity.getWouldHaveR4h()).isNull();
        assertThat(opportunity.getWouldHaveR24h()).isNull();
        assertThat(opportunity.isScored()).isTrue();
    }
}
