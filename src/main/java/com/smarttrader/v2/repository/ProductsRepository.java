package com.smarttrader.v2.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.CoinDocument;

@Repository
public interface ProductsRepository extends MongoRepository<CoinDocument, String> {

}
