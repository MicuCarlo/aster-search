package com.astersearch.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class TokenizerTest {

    @Test
    void handlesUnicodeNumbersNullAndLocaleIndependently() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals(List.of("i", "café", "123", "東京"), tokenizer.tokenize("I Café 123 東京"));
            assertEquals(List.of(), tokenizer.tokenize(null));
            assertEquals(List.of(), tokenizer.tokenize("---"));
        } finally { java.util.Locale.setDefault(previous); }
    }

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
