package com.astersearch.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class TokenizerTest {

    private final Tokenizer tokenizer = new Tokenizer();

    @Test
    void convertsTextIntoNormalisedTokens() {
        List<String> tokens =
                tokenizer.tokenize("Distributed Systems are RELIABLE.");

        assertEquals(
                List.of("distributed", "systems", "are", "reliable"),
                tokens
        );
    }

    @Test
    void handlesEmptyText() {
        assertEquals(List.of(), tokenizer.tokenize(""));
    }

    @Test
    void handlesPunctuation() {
        assertEquals(
                List.of("java", "search", "engine"),
                tokenizer.tokenize("Java, search-engine!")
        );
    }
}