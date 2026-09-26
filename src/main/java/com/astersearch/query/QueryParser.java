package com.astersearch.query;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import com.astersearch.text.Tokenizer;

/** Recursive descent: OR -> AND (including adjacency) -> NOT -> word/group. */
public final class QueryParser {
    private static final Pattern PART = Pattern.compile("[\\p{L}\\p{N}]+|[()]");
    private final Tokenizer tokenizer = new Tokenizer();
    private final List<String> parts = new ArrayList<>();
    private int position;

    private QueryParser(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Query cannot be blank");
        }
        if (input.length() > 4096) {
            throw new IllegalArgumentException("Query exceeds 4096 characters");
        }
        var matcher = PART.matcher(input);
        int end = 0;
        while (matcher.find()) {
            String gap = input.substring(end, matcher.start());
            if (!gap.isEmpty() && !gap.isBlank()) {
                throw new IllegalArgumentException("Queries support words, whitespace and parentheses only");
            }
            parts.add(matcher.group());
            end = matcher.end();
        }
        String tail = input.substring(end);
        if (!tail.isEmpty() && !tail.isBlank()) {
            throw new IllegalArgumentException("Unsupported query punctuation");
        }
        if (parts.size() > 256) {
            throw new IllegalArgumentException("Query exceeds 256 tokens");
        }
    }

    public static Query parse(String input) {
        QueryParser parser = new QueryParser(input);
        Query query = parser.parseOr();
        if (parser.position != parser.parts.size()) {
            throw parser.error("Unexpected token");
        }
        return query;
    }

    private Query parseOr() {
        Query query = parseAnd();
        while (take("OR")) {
            query = new Query.Or(query, parseAnd());
        }
        return query;
    }

    private Query parseAnd() {
        Query query = parseUnary();
        while (position < parts.size() && !peek("OR") && !peek(")")) {
            take("AND"); // An omitted operator between operands also means AND.
            query = new Query.And(query, parseUnary());
        }
        return query;
    }

    private Query parseUnary() {
        if (take("NOT")) {
            return new Query.Not(parseUnary());
        }
        if (take("(")) {
            Query query = parseOr();
            if (!take(")")) {
                throw error("Expected closing parenthesis");
            }
            return query;
        }
        if (position == parts.size() || peek("AND") || peek("OR") || peek(")")) {
            throw error("Expected a word or group");
        }

        List<String> words = tokenizer.tokenize(parts.get(position));
        if (words.isEmpty()) {
            throw error("Word has no searchable tokens after normalisation");
        }
        position++;

        // Unicode lowercasing can split one lexical word (e.g. İSTANBUL).
        // Return the whole expansion as one operand so NOT negates all of it.
        Query query = new Query.Term(words.getFirst());
        for (int wordIndex = 1; wordIndex < words.size(); wordIndex++) {
            query = new Query.And(query, new Query.Term(words.get(wordIndex)));
        }
        return query;
    }

    private boolean peek(String token) {
        return position < parts.size() && parts.get(position).equals(token);
    }

    private boolean take(String token) {
        if (!peek(token)) {
            return false;
        }
        position++;
        return true;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at query token " + (position + 1));
    }
}
