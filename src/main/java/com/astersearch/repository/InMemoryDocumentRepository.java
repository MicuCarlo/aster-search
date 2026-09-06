package com.astersearch.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.astersearch.model.Document;

public final class InMemoryDocumentRepository
        implements DocumentRepository {

    private final Map<Integer, Document> documents;

    public InMemoryDocumentRepository() {
        this.documents = new HashMap<>();
    }

    @Override
    public void add(Document document) {
        if (document == null){
            throw new IllegalArgumentException("Document cannot be null");
        }

        if (documents.containsKey(document.id())){
            throw new IllegalArgumentException("Document already exists");
        }

        documents.put(document.id(), document);
    }

    @Override
    public Optional<Document> findById(int id) {
        return Optional.ofNullable(documents.get(id));
    }

    @Override
    public int documentCount() {
        return documents.size();
    }
}