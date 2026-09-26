package com.astersearch.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import com.astersearch.model.Document;
import com.astersearch.query.Query;
import com.astersearch.query.QueryParser;
import com.astersearch.search.SearchEngine;
import com.astersearch.storage.SnapshotStore;
import com.astersearch.storage.TextImporter;
import com.astersearch.text.Tokenizer;

/** Reproducible synthetic matching, ranked-search and snapshot-load measurements. */
public final class Benchmark {
    private static final int WARMUP_ROUNDS = 3;
    private static final int SAMPLE_ROUNDS = 7;
    private static final List<String> QUERIES = List.of(
            "rare", "word42 word73", "topic3 OR topic7", "common AND NOT topic5",
            "(topic1 OR topic2) AND common", "NOT common");
    private static volatile Object consumedResult;

    private Benchmark() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Benchmark <new-corpus-directory>");
        }
        Path directory = Path.of(args[0]);
        generate(directory, 10_000);

        long start = System.nanoTime();
        SearchEngine engine = TextImporter.load(directory);
        double buildMilliseconds = elapsed(start);
        List<Document> documents = engine.documents();

        start = System.nanoTime();
        Map<Integer, Set<String>> documentWords = pretokenize(documents);
        double preprocessingMilliseconds = elapsed(start);
        long wordMemberships = documentWords.values().stream().mapToLong(Set::size).sum();

        printEnvironment(directory, documents.size());
        System.out.printf(Locale.ROOT, "Import and index milliseconds=%.3f%n", buildMilliseconds);
        System.out.printf(Locale.ROOT, "Pretokenized scan preprocessing milliseconds=%.3f; retained word memberships=%d%n",
                preprocessingMilliseconds, wordMemberships);

        // Complete all equivalence checks before any query timing begins.
        List<Query> parsedQueries = new ArrayList<>();
        for (String text : QUERIES) {
            Query query = QueryParser.parse(text);
            Set<Integer> expected = scan(documents, query);
            Set<Integer> rankedIds = new HashSet<>();
            engine.search(text).forEach(result -> rankedIds.add(result.document().id()));
            if (!expected.equals(engine.matchingIds(query))
                    || !expected.equals(scanPretokenized(documentWords, query))
                    || !expected.equals(rankedIds)) {
                throw new AssertionError("Semantic mismatch: " + text);
            }
            parsedQueries.add(query);
        }
        System.out.println("Equivalence verified for all queries: indexed, retokenizing scan, pretokenized scan, ranked result IDs.");
        System.out.println("3 warmup rounds and 7 measured rounds/query; four methods in rotating order.");
        System.out.println("Matching methods use parsed queries; ranked search includes parsing, matching, BM25 and sorting.");

        for (int queryIndex = 0; queryIndex < QUERIES.size(); queryIndex++) {
            String text = QUERIES.get(queryIndex);
            Query query = parsedQueries.get(queryIndex);
            List<Supplier<?>> operations = List.of(
                    () -> engine.matchingIds(query),
                    () -> scan(documents, query),
                    () -> scanPretokenized(documentWords, query),
                    () -> engine.search(text));
            String[] names = {"indexed matching", "retokenizing scan matching", "pretokenized scan matching", "ranked search"};
            double[][] samples = new double[operations.size()][SAMPLE_ROUNDS];
            for (int round = 0; round < WARMUP_ROUNDS + SAMPLE_ROUNDS; round++) {
                // Rotate first position to reduce systematic method-order bias.
                for (int offset = 0; offset < operations.size(); offset++) {
                    int method = (round + offset) % operations.size();
                    double milliseconds = measure(operations.get(method));
                    if (round >= WARMUP_ROUNDS) {
                        samples[method][round - WARMUP_ROUNDS] = milliseconds;
                    }
                }
            }
            System.out.println("Query=" + text + "; matches=" + engine.matchingIds(query).size());
            for (int method = 0; method < operations.size(); method++) {
                printSamples(names[method], samples[method]);
            }
        }

        // This newly generated directory is ours; the .ast file is excluded from text import.
        Path snapshot = directory.resolve("benchmark.ast");
        SnapshotStore.save(engine, snapshot);
        SearchEngine restored = SnapshotStore.load(snapshot);
        if (!engine.documents().equals(restored.documents())) {
            throw new AssertionError("Snapshot documents differ");
        }
        for (String text : QUERIES) {
            if (!engine.search(text).equals(restored.search(text))) {
                throw new AssertionError("Snapshot ranked results differ: " + text);
            }
        }
        System.out.println("Snapshot round-trip verified; bytes=" + Files.size(snapshot));
        double[] loadSamples = new double[SAMPLE_ROUNDS];
        for (int round = 0; round < WARMUP_ROUNDS + SAMPLE_ROUNDS; round++) {
            start = System.nanoTime();
            SearchEngine loaded = SnapshotStore.load(snapshot);
            double milliseconds = elapsed(start);
            consumedResult = loaded;
            if (round >= WARMUP_ROUNDS) {
                loadSamples[round - WARMUP_ROUNDS] = milliseconds;
            }
        }
        printSamples("snapshot load (file read, validation and index rebuild; warm OS cache)", loadSamples);
    }

    private static void printEnvironment(Path directory, int documentCount) throws Exception {
        long bytes = 0;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                byte[] content = Files.readAllBytes(file);
                bytes += content.length;
                digest.update(content);
            }
        }
        System.out.println("Synthetic corpus: generator v1; Random seed 20260925; documents=" + documentCount);
        System.out.println("UTF-8 content bytes=" + bytes + "; concatenated sorted content SHA-256="
                + HexFormat.of().formatHex(digest.digest()));
        System.out.println("Java=" + System.getProperty("java.runtime.version") + "; VM=" + System.getProperty("java.vm.name"));
        System.out.println("OS=" + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        System.out.println("Available processors=" + Runtime.getRuntime().availableProcessors()
                + "; max heap bytes=" + Runtime.getRuntime().maxMemory());
    }

    public static void generate(Path directory, int count) throws IOException {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }
        // Refuse existing directories so a benchmark never overwrites user documents.
        Files.createDirectory(directory);
        Random random = new Random(20260925L);
        for (int id = 0; id < count; id++) {
            StringBuilder content = new StringBuilder("common topic").append(id % 20);
            if (id % 100 == 0) {
                content.append(" rare");
            }
            for (int word = 0; word < 120; word++) {
                content.append(" word").append(random.nextInt(1000));
            }
            content.append('\n');
            Files.writeString(directory.resolve(String.format(Locale.ROOT, "doc%05d.txt", id)), content);
        }
    }

    /** Baseline that pays the cost of tokenising every document on every query. */
    public static Set<Integer> scan(List<Document> documents, Query query) {
        Set<Integer> matches = new HashSet<>();
        Tokenizer tokenizer = new Tokenizer();
        for (Document document : documents) {
            Set<String> words = new HashSet<>(tokenizer.tokenize(document.title()));
            words.addAll(tokenizer.tokenize(document.content()));
            if (query.matches(words::contains)) {
                matches.add(document.id());
            }
        }
        return matches;
    }

    /** Retains a vocabulary per document; call outside query timing. */
    public static Map<Integer, Set<String>> pretokenize(List<Document> documents) {
        Map<Integer, Set<String>> documentWords = new LinkedHashMap<>();
        Tokenizer tokenizer = new Tokenizer();
        for (Document document : documents) {
            Set<String> words = new HashSet<>(tokenizer.tokenize(document.title()));
            words.addAll(tokenizer.tokenize(document.content()));
            documentWords.put(document.id(), words);
        }
        return documentWords;
    }

    public static Set<Integer> scanPretokenized(Map<Integer, Set<String>> documentWords, Query query) {
        Set<Integer> matches = new HashSet<>();
        for (var document : documentWords.entrySet()) {
            if (query.matches(document.getValue()::contains)) {
                matches.add(document.getKey());
            }
        }
        return matches;
    }

    private static double measure(Supplier<?> operation) {
        long start = System.nanoTime();
        Object result = operation.get();
        double milliseconds = elapsed(start);
        // Publish the complete result outside timing to prevent dead-code elimination.
        consumedResult = result;
        return milliseconds;
    }

    private static void printSamples(String name, double[] samples) {
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        System.out.printf(Locale.ROOT, "%s: median ms=%.3f; samples ms=%s%n",
                name, sorted[sorted.length / 2], Arrays.toString(samples));
    }

    private static double elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }
}
