package com.smarttrader.v2.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.smarttrader.v2.model.Order;

/**
 * Spring Data MongoDB repository for {@link Order} documents.
 */
public interface OrderRepository extends MongoRepository<Order, String> {

	List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

	List<Order> findByUserIdAndProductIdOrderByCreatedAtDesc(String userId, String productId);

	Optional<Order> findByCoinbaseOrderId(String coinbaseOrderId);

	List<Order> findByProductId(String productId);

	List<Order> findByProductIdAndSide(String productId, String side);
}
