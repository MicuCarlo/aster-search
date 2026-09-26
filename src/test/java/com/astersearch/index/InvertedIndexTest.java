package com.astersearch.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.astersearch.model.Document;
import com.astersearch.text.Tokenizer;

class InvertedIndexTest {

    @Test
    void storesFrequenciesLengthsAndImmutablePostings() {
        InvertedIndex index = new InvertedIndex(new Tokenizer());
        index.add(new Document(1, "Java", "java search"));
        index.add(new Document(2, "!", ""));
        assertEquals(2, index.posting("java").get(1));
        assertEquals(3, index.documentLength(1));
        assertEquals(0, index.documentLength(2));
        assertEquals(1.5, index.averageLength());
        assertThrows(UnsupportedOperationException.class, () -> index.posting("java").put(3, 10));
    }

    private InvertedIndex index;

    @BeforeEach
    void setUp() {
        index = new InvertedIndex(new Tokenizer());
    }

    @Test
    void indexesWordsFromDocumentTitleAndContent() {
        Document document = new Document(
                1,
                "Java Concurrency",
                "Threads execute work concurrently."
        );

        index.add(document);

        assertEquals(Set.of(1), index.findDocumentIds("java"));
        assertEquals(Set.of(1), index.findDocumentIds("threads"));
    }

    @Test
    void returnsEveryDocumentContainingTerm() {
        index.add(new Document(
                1,
                "Java",
                "Distributed systems"
        ));

        index.add(new Document(
                2,
                "Python",
                "Reliable systems"
        ));

        assertEquals(Set.of(1, 2), index.findDocumentIds("systems"));
    }

    @Test
    void doesNotDuplicateIdWhenTermAppearsRepeatedly() {
        index.add(new Document(
                1,
                "Java Java",
                "Java is used here several times: Java."
        ));

        assertEquals(Set.of(1), index.findDocumentIds("java"));
    }

    @Test
    void normalisesSearchTerm() {
        index.add(new Document(
                1,
                "Java",
                "Search engine"
        ));

        assertEquals(Set.of(1), index.findDocumentIds("JAVA!"));
    }

    @Test
    void returnsEmptySetForUnknownTerm() {
        index.add(new Document(
                1,
                "Java",
                "Search engine"
        ));

        assertEquals(Set.of(), index.findDocumentIds("python"));
    }

    @Test
    void returnsEmptySetForBlankSearchTerm() {
        assertEquals(Set.of(), index.findDocumentIds("   "));
    }

    @Test
    void rejectsNullDocument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> index.add(null)
        );
    }

    @Test
    void rejectsDuplicateDocumentId() {
        index.add(new Document(1, "First", "First content"));

        assertThrows(
                IllegalArgumentException.class,
                () -> index.add(
                        new Document(1, "Second", "Different content")
                )
        );
    }

    @Test
    void reportsNumberOfIndexedDocuments() {
        index.add(new Document(1, "First", "Content"));
        index.add(new Document(2, "Second", "Content"));

        assertEquals(2, index.documentCount());
    }

    @Test
    void doesNotExposeMutableInternalSet() {
        index.add(new Document(1, "Java", "Search engine"));

        Set<Integer> results = index.findDocumentIds("java");

        assertThrows(
                UnsupportedOperationException.class,
                () -> results.add(99)
        );
    }

    @Test
    void rejectsNullTokenizer() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InvertedIndex(null)
        );
    }

    @Test
    void rejectsMultipleSearchTerms() {
        assertThrows(
                IllegalArgumentException.class,
                () -> index.findDocumentIds("java python")
        );
    }
}
