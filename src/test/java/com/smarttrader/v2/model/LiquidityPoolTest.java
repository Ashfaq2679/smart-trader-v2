package com.smarttrader.v2.model;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0.7 gate: "MongoDB TTL index configuration verified". Doesn't require a live
 * Mongo instance: confirms the TTL annotation itself is declared correctly on the one
 * document Phase 0 introduces (liquidity_pools, TTL 5 days per the plan).
 */
class LiquidityPoolTest {

    @Test
    void bullish_liquidityPoolsCollectionIsAnnotatedForMongo() {
        Document document = LiquidityPool.class.getAnnotation(Document.class);

        assertThat(document).isNotNull();
        assertThat(document.value()).isEqualTo("liquidity_pools");
    }

    @Test
    void bullish_expiresAtHasATtlIndexOfZeroSeconds() throws NoSuchFieldException {
        Field field = LiquidityPool.class.getDeclaredField("expiresAt");
        Indexed indexed = field.getAnnotation(Indexed.class);

        assertThat(indexed).isNotNull();
        assertThat(indexed.expireAfterSeconds()).isZero();
    }

    @Test
    void edgeCase_builderProducesAConstructableDocument() {
        LiquidityPool pool = LiquidityPool.builder()
                .symbol("BTC-USD")
                .type(PoolType.EQL)
                .density(80f)
                .touches(3)
                .build();

        assertThat(pool.getSymbol()).isEqualTo("BTC-USD");
        assertThat(pool.getType()).isEqualTo(PoolType.EQL);
        assertThat(pool.getTouches()).isEqualTo(3);
    }
}
