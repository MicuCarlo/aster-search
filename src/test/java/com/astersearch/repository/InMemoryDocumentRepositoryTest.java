package com.astersearch.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.astersearch.model.Document;

class InMemoryDocumentRepositoryTest {

    private InMemoryDocumentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDocumentRepository();
    }

    @Test
    void startsEmpty() {
        assertEquals(0, repository.documentCount());
    }

    @Test
    void storesAndFindsDocumentById() {
        Document document = new Document(
                1,
                "Java Search",
                "A search engine written in Java."
        );

        repository.add(document);

        assertEquals(
                Optional.of(document),
                repository.findById(1)
        );
    }

    @Test
    void returnsEmptyOptionalForUnknownId() {
        assertEquals(
                Optional.empty(),
                repository.findById(99)
        );
    }

    @Test
    void rejectsNullDocument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.add(null)
        );
    }

    @Test
    void rejectsDuplicateDocumentId() {
        repository.add(
                new Document(1, "First", "First content")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.add(
                        new Document(1, "Second", "Second content")
                )
        );
    }

    @Test
    void reportsStoredDocumentCount() {
        repository.add(new Document(1, "First", "Content"));
        repository.add(new Document(2, "Second", "Content"));

        assertEquals(2, repository.documentCount());
    }
}