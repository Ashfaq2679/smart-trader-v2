package com.smarttrader.v2.model;

import org.springframework.data.mongodb.core.mapping.Document;

@Document("coins")
public record ProductDocument(String coin, Integer rank, String productId) {
}
