package com.astersearch.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DocumentTest {

    @Test
    void storesDocumentData() {
        Document document = new Document(
                7,
                "Java Concurrency",
                "Threads can execute work concurrently."
        );

        assertAll(
                () -> assertEquals(7, document.id()),
                () -> assertEquals("Java Concurrency", document.title()),
                () -> assertEquals(
                        "Threads can execute work concurrently.",
                        document.content()
                )
        );
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Document(-1, "Title", "Content")
        );
    }

    @Test
    void rejectsNullTitle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Document(1, null, "Content")
        );
    }

    @Test
    void rejectsBlankTitle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Document(1, "   ", "Content")
        );
    }

    @Test
    void rejectsNullContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Document(1, "Title", null)
        );
    }
}