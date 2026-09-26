package com.astersearch.search;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.astersearch.index.InvertedIndex;
import com.astersearch.model.Document;
import com.astersearch.query.Query;
import com.astersearch.query.QueryParser;
import com.astersearch.repository.DocumentRepository;
import com.astersearch.repository.InMemoryDocumentRepository;
import com.astersearch.storage.SnapshotStore;
import com.astersearch.text.Tokenizer;

/** Owns repository and index together, keeping their IDs consistent. */
public final class SearchEngine {
    private final DocumentRepository documents = new InMemoryDocumentRepository();
    private final InvertedIndex index = new InvertedIndex(new Tokenizer());
    public static final double K1 = 1.2;
    public static final double B = 0.75;

    public void add(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (documents.findById(document.id()).isPresent()) {
            throw new IllegalArgumentException("Duplicate document ID: " + document.id());
        }
        index.add(document);
        documents.add(document);
    }

    public List<Document> documents() {
        return index.documentIds().stream()
                .sorted()
                .map(id -> documents.findById(id).orElseThrow())
                .toList();
    }

    public int documentCount() {
        return index.documentCount();
    }

    public Set<Integer> matchingIds(Query query) {
        return query.evaluate(term -> index.posting(term).keySet(), index.documentIds());
    }

    public List<SearchResult> search(String query) {
        return search(QueryParser.parse(query));
    }

    public List<SearchResult> search(Query query) {
        Set<String> terms = query.positiveTerms();
        return matchingIds(query).stream()
                .map(id -> new SearchResult(documents.findById(id).orElseThrow(), score(id, terms)))
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed()
                        .thenComparingInt(result -> result.document().id()))
                .toList();
    }

    private double score(int id, Set<String> terms) {
        double score = 0;
        for (String term : terms) {
            Map<Integer, Integer> posting = index.posting(term);
            int frequency = posting.getOrDefault(id, 0);
            if (frequency == 0) {
                // Also avoids dividing by zero when the corpus has no tokens.
                continue;
            }
            double inverseFrequency = Math.log(1 + (index.documentCount() - posting.size() + 0.5)
                    / (posting.size() + 0.5));
            double normalisation = K1 * (1 - B + B * index.documentLength(id) / index.averageLength());
            score += inverseFrequency * frequency * (K1 + 1) / (frequency + normalisation);
        }
        return score;
    }

    /** Canonical index data used to validate persisted statistics. */
    public void writeIndex(DataOutput output) throws IOException {
        for (Document document : documents()) {
            output.writeInt(index.documentLength(document.id()));
        }
        output.writeInt(index.terms().size());
        // Sorting here makes the snapshot independent of hash-map iteration order.
        for (String term : new TreeSet<>(index.terms())) {
            SnapshotStore.writeText(output, term);
            Map<Integer, Integer> posting = new TreeMap<>(index.posting(term));
            output.writeInt(posting.size());
            for (var entry : posting.entrySet()) {
                output.writeInt(entry.getKey());
                output.writeInt(entry.getValue());
            }
        }
    }
}
