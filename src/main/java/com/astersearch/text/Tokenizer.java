package com.astersearch.text;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Tokenizer {

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return Arrays.stream(
                    text.toLowerCase(Locale.ROOT)
                        .split("[^\\p{L}\\p{N}]+")
                )
                .filter(token -> !token.isBlank())
                .toList();
    }
}