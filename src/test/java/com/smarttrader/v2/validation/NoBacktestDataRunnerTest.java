package com.smarttrader.v2.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoBacktestDataRunnerTest {

    private final NoBacktestDataRunner runner = new NoBacktestDataRunner();

    @Test
    void sideways_alwaysReturnsNoDataResultRegardlessOfInput() {
        BacktestResult result = runner.run("PullbackStrategy", "BTC-USD");

        assertThat(result.trades()).isZero();
        assertThat(result.expectancy()).isZero();
        assertThat(result.monteCarloRoR()).isEqualTo(1.0);
    }
}
