package com.smarttrader.v2.orchestration;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for TradingScheduler, under the "trading.scheduler" prefix.
 *
 * Disabled by default (enabled=false): a live-order-placing scheduler must be an
 * explicit opt-in, never something that starts firing just because the app started.
 *
 * Example application.yml:
 *   trading:
 *     scheduler:
 *       enabled: true
 *       product-ids: [BTC-USD, ETH-USD]
 *       granularity: ONE_HOUR
 *       fixed-delay-ms: 60000
 *       user-id: my-user
 *       capital: 10000
 *       risk-percent: 0.01
 */
@ConfigurationProperties(prefix = "trading.scheduler")
public record SchedulerProperties(
        @DefaultValue("false") boolean enabled,
        List<String> excludedProductIds,
        @DefaultValue("FIFTEEN_MINUTE") String granularity,
        @DefaultValue("60000") long fixedDelayMs,
        String userId,
        @DefaultValue("10000") double capital,
        @DefaultValue("0.01") double riskPercent
) {
	public SchedulerProperties {
		if (excludedProductIds == null) {
			excludedProductIds = List.of();
		}
	}
}
