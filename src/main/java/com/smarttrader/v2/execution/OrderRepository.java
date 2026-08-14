package com.smarttrader.v2.execution;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.Order;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findBySymbolOrderByCreatedAtDesc(String symbol);
}
