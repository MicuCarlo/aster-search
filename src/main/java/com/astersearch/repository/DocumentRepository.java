package com.astersearch.repository;

import java.util.Optional;

import com.astersearch.model.Document;

public interface DocumentRepository {

    void add(Document document);

    Optional<Document> findById(int id);

    int documentCount();
}