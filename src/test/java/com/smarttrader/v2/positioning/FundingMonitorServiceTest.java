package com.smarttrader.v2.positioning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FundingMonitorServiceTest {

    private final FundingMonitorService service = new FundingMonitorService();

    @Test
    void edgeCase_noDataReturnsNeutralFiftiethPercentileAndZeroRate() {
        assertThat(service.getFundingPercentile30d("BTC-USD")).isEqualTo(50);
        assertThat(service.getCurrentFundingRateBps("BTC-USD")).isZero();
    }

    @Test
    void bullish_currentRateIsTheMostRecentlyStoredValue() {
        service.storeFunding("BTC-USD", 5.0);
        service.storeFunding("BTC-USD", 8.0);

        assertThat(service.getCurrentFundingRateBps("BTC-USD")).isEqualTo(8.0);
    }

    @Test
    void bullish_percentileReflectsWhereTheCurrentRateSitsInHistory() {
        // 5 lower values then the current (highest) one -> current is at the 100th percentile
        for (double rate : new double[]{1, 2, 3, 4, 5}) {
            service.storeFunding("BTC-USD", rate);
        }
        service.storeFunding("BTC-USD", 100.0);

        assertThat(service.getFundingPercentile30d("BTC-USD")).isEqualTo(100);
    }

    @Test
    void bearish_lowestRateInHistorySitsAtALowPercentile() {
        service.storeFunding("BTC-USD", -10.0);
        for (double rate : new double[]{1, 2, 3, 4, 5}) {
            service.storeFunding("BTC-USD", rate);
        }
        service.storeFunding("BTC-USD", -10.0);

        assertThat(service.getFundingPercentile30d("BTC-USD")).isLessThanOrEqualTo(50);
    }

    @Test
    void edgeCase_percentileCacheIsInvalidatedWhenNewDataIsStored() {
        service.storeFunding("BTC-USD", 1.0);
        int firstPercentile = service.getFundingPercentile30d("BTC-USD");
        assertThat(firstPercentile).isEqualTo(100); // sole/highest value so far

        service.storeFunding("BTC-USD", 1000.0);
        int secondPercentile = service.getFundingPercentile30d("BTC-USD");

        assertThat(secondPercentile).isEqualTo(100); // now the new value is the highest
    }
}
