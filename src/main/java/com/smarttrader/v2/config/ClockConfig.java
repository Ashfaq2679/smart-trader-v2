package com.smarttrader.v2.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The system's single Clock bean, per V2_TECH_SPEC_v1.1.md's Final Insight: "System must
 * behave identically in live trading, replay mode, and backtesting." Live/replay wiring
 * uses this default (real, system UTC) clock; a backtest harness supplies its own fixed
 * or simulated Clock instead of touching Instant.now() directly anywhere in the pipeline.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
