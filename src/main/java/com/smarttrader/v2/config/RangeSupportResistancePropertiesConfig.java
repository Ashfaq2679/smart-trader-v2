package com.smarttrader.v2.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.smarttrader.v2.strategy.range.RangeSupportResistanceConfig;

/**
 * Registers {@link RangeSupportResistanceConfig} as a Spring bean bound to the
 * {@code smart-trader.v2_5.strategies.range-support-resistance} YAML block, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL_WITH_RANGE_SR.md §2.3.1 lines 776-795.
 * Additive: no other config classes are touched.
 */
@Configuration
@EnableConfigurationProperties(RangeSupportResistanceConfig.class)
public class RangeSupportResistancePropertiesConfig {
}
