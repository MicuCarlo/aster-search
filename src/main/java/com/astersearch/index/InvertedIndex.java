package com.astersearch.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.astersearch.model.Document;
import com.astersearch.text.Tokenizer;

/** A posting maps document IDs to term frequencies. Title and content have equal weight. */
public final class InvertedIndex {
    private final Tokenizer tokenizer;
    private final Map<String, Map<Integer, Integer>> postings = new HashMap<>();
    private final Map<Integer, Integer> lengths = new HashMap<>();
    private long totalLength;

    public InvertedIndex(Tokenizer tokenizer) {
        if (tokenizer == null) {
            throw new IllegalArgumentException("Tokenizer cannot be null");
        }
        this.tokenizer = tokenizer;
    }

    public void add(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (lengths.containsKey(document.id())) {
            throw new IllegalArgumentException("Document ID has already been indexed");
        }
        List<String> words = new ArrayList<>(tokenizer.tokenize(document.title()));
        words.addAll(tokenizer.tokenize(document.content()));
        for (String word : words) {
            Map<Integer, Integer> posting = postings.computeIfAbsent(word, ignored -> new HashMap<>());
            posting.merge(document.id(), 1, Integer::sum);
        }
        // Tokenless documents still belong to the universe used by NOT queries.
        lengths.put(document.id(), words.size());
        totalLength += words.size();
    }

    public Set<Integer> findDocumentIds(String term) {
        List<String> words = tokenizer.tokenize(term);
        if (words.size() > 1) {
            throw new IllegalArgumentException("Expected one search term");
        }
        return words.isEmpty() ? Set.of() : Set.copyOf(posting(words.getFirst()).keySet());
    }

    /** Read-only view; term must already be normalised. */
    public Map<Integer, Integer> posting(String term) {
        return Collections.unmodifiableMap(postings.getOrDefault(term, Map.of()));
    }

    public Set<String> terms() {
        return Set.copyOf(postings.keySet());
    }

    public Set<Integer> documentIds() {
        return Set.copyOf(lengths.keySet());
    }

    public int documentLength(int id) {
        return lengths.getOrDefault(id, 0);
    }

    public int documentCount() {
        return lengths.size();
    }

    public double averageLength() {
        return lengths.isEmpty() ? 0 : (double) totalLength / lengths.size();
    }
}
