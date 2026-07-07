package com.smarttrader.v2.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyKeyStoreTest {

    private final InMemoryIdempotencyKeyStore store = new InMemoryIdempotencyKeyStore();

    @Test
    void missingKeyReturnsEmpty() {
        assertThat(store.find("missing")).isEmpty();
    }

    @Test
    void savedResultIsReturnedForSameKey() {
        OrderResult result = OrderResult.builder().idempotencyKey("k").status(OrderStatus.PLACED).build();

        store.save("k", result);

        assertThat(store.find("k")).contains(result);
    }

    @Test
    void savingAgainForSameKeyOverwritesThePreviousResult() {
        OrderResult first = OrderResult.builder().idempotencyKey("k").status(OrderStatus.CANCELLED).build();
        OrderResult second = OrderResult.builder().idempotencyKey("k").status(OrderStatus.PLACED).build();

        store.save("k", first);
        store.save("k", second);

        assertThat(store.find("k")).contains(second);
    }
}
