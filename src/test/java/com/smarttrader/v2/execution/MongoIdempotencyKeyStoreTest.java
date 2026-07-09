package com.smarttrader.v2.execution;

import com.smarttrader.v2.model.TradeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoIdempotencyKeyStoreTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    private MongoIdempotencyKeyStore store;

    @BeforeEach
    void setUp() {
        store = new MongoIdempotencyKeyStore(idempotencyRepository);
    }

    @Test
    void missingKeyReturnsEmpty() {
        when(idempotencyRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(store.find("missing")).isEmpty();
    }

    @Test
    void savedResultRoundTripsThroughTheDocumentMapping() {
        OrderResult result = OrderResult.builder()
                .idempotencyKey("key-1")
                .productId("BTC-USD")
                .status(OrderStatus.PLACED)
                .reason("placed")
                .direction(TradeDirection.LONG)
                .requestedPrice(100.0)
                .quotedPrice(100.2)
                .slippage(0.002)
                .positionSize(10)
                .evaluatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        store.save("key-1", result);

        org.mockito.ArgumentCaptor<IdempotencyRecordDocument> captor = org.mockito.ArgumentCaptor.forClass(IdempotencyRecordDocument.class);
        verify(idempotencyRepository).save(captor.capture());
        IdempotencyRecordDocument saved = captor.getValue();
        assertThat(saved.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(saved.getStatus()).isEqualTo("PLACED");

        when(idempotencyRepository.findById("key-1")).thenReturn(Optional.of(saved));
        assertThat(store.find("key-1")).contains(result);
    }
}
