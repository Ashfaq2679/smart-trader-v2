package com.smarttrader.v2.execution;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.smarttrader.v2.model.TradeDirection;

import lombok.RequiredArgsConstructor;

/**
 * Durable IdempotencyKeyStore backed by MongoDB, per V2_TECH_SPEC_v1.1.md section 11
 * ("Resume without duplicate orders"): unlike InMemoryIdempotencyKeyStore, a restart
 * doesn't lose which order-submission attempts have already been processed.
 *
 * @Primary so this wins over InMemoryIdempotencyKeyStore for autowiring; the in-memory
 * store remains available (and still directly testable/usable) as a lighter-weight fallback.
 */
@Component
@Primary
@RequiredArgsConstructor
public class MongoIdempotencyKeyStore implements IdempotencyKeyStore {

	private final IdempotencyRepository idempotencyRepository;

	@Override
	public Optional<OrderResult> find(String idempotencyKey) {
		return idempotencyRepository.findById(idempotencyKey).map(MongoIdempotencyKeyStore::toResult);
	}

	@Override
	public void save(String idempotencyKey, OrderResult result) {
		idempotencyRepository.save(toDocument(idempotencyKey, result));
	}

	private static IdempotencyRecordDocument toDocument(String idempotencyKey, OrderResult result) {
		IdempotencyRecordDocument document = new IdempotencyRecordDocument();
		document.setIdempotencyKey(idempotencyKey);
		document.setProductId(result.productId());
		document.setStatus(result.status() == null ? null : result.status().name());
		document.setReason(result.reason());
		document.setDirection(result.direction() == null ? null : result.direction().name());
		document.setRequestedPrice(result.requestedPrice());
		document.setQuotedPrice(result.quotedPrice());
		document.setSlippage(result.slippage());
		document.setPositionSize(result.positionSize());
		document.setEvaluatedAt(result.evaluatedAt());
		return document;
	}

	private static OrderResult toResult(IdempotencyRecordDocument document) {
		return OrderResult.builder()
				.idempotencyKey(document.getIdempotencyKey())
				.productId(document.getProductId())
				.status(document.getStatus() == null ? null : OrderStatus.valueOf(document.getStatus()))
				.reason(document.getReason())
				.direction(document.getDirection() == null ? null : TradeDirection.valueOf(document.getDirection()))
				.requestedPrice(document.getRequestedPrice())
				.quotedPrice(document.getQuotedPrice())
				.slippage(document.getSlippage())
				.positionSize(document.getPositionSize())
				.evaluatedAt(document.getEvaluatedAt())
				.build();
	}
}
