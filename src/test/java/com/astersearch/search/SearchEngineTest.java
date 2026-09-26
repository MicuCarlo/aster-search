package com.astersearch.search;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.astersearch.model.Document;
import com.astersearch.query.QueryParser;
import com.astersearch.benchmark.Benchmark;

class SearchEngineTest {
    private SearchEngine example() {
        SearchEngine engine = new SearchEngine();
        engine.add(new Document(1, "!", "java java search"));
        engine.add(new Document(2, "!", "java python"));
        engine.add(new Document(3, "!", "python"));
        engine.add(new Document(4, "!", ""));
        return engine;
    }
    private Set<Integer> ids(String query) { return example().matchingIds(QueryParser.parse(query)); }
    @Test void evaluatesPrecedenceAdjacencyAndGroups() {
        assertEquals(Set.of(1), ids("JAVA search"));
        assertEquals(Set.of(1, 2), ids("java OR python AND search"));
        assertEquals(Set.of(1), ids("(java OR python) AND search"));
        assertEquals(Set.of(1), ids("java NOT python"));
        assertEquals(Set.of(3, 4), ids("NOT java"));
        assertEquals(Set.of(1, 2), ids("NOT NOT java"));
        assertEquals(Set.of(), ids("java AND NOT java"));
        assertEquals(Set.of(1, 2, 3, 4), ids("java OR NOT java"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "AND java", "java OR", "()", "(java", "java)", "java AND OR python", "NOT", "java!", "\"java\"", "java + python"})
    void rejectsInvalidQueries(String query) { assertThrows(IllegalArgumentException.class, () -> example().search(query)); }
    @Test void rejectsNullAndExcessiveQueries() {
        assertThrows(IllegalArgumentException.class, () -> QueryParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> QueryParser.parse("a".repeat(4097)));
        assertThrows(IllegalArgumentException.class, () -> QueryParser.parse("NOT ".repeat(256) + "a"));
    }
    @Test void matchesHandCalculatedBm25AndDeduplicatesQueryTerms() {
        SearchEngine engine = new SearchEngine();
        engine.add(new Document(1, "!", "java java search"));
        engine.add(new Document(2, "!", "java python"));
        engine.add(new Document(3, "!", "python"));
        List<SearchResult> results = engine.search("java");
        assertEquals(List.of(1, 2), results.stream().map(r -> r.document().id()).toList());
        assertEquals(Math.log(1.6) * 4.4 / 3.65, results.get(0).score(), 1e-12);
        assertEquals(Math.log(1.6), results.get(1).score(), 1e-12);
        assertEquals(results, engine.search("java java"));
        assertEquals(results, engine.search("NOT NOT java"));
    }
    @Test void negativeTermsDoNotScoreAndTiesUseId() {
        SearchEngine engine = example();
        List<SearchResult> results = engine.search("NOT missing");
        assertEquals(List.of(1, 2, 3, 4), results.stream().map(r -> r.document().id()).toList());
        assertTrue(results.stream().allMatch(r -> r.score() == 0));
        assertEquals(engine.search("java NOT python").getFirst().score(), engine.search("java").getFirst().score());
    }
    @Test void frequencyAndLengthInfluenceRanking() {
        SearchEngine engine = new SearchEngine();
        engine.add(new Document(8, "!", "java"));
        engine.add(new Document(2, "!", "java java"));
        engine.add(new Document(1, "!", "java " + "filler ".repeat(20)));
        assertEquals(List.of(2, 8, 1), engine.search("java").stream().map(r -> r.document().id()).toList());
    }
    @Test void agreesWithScanAcrossRandomDocumentsAndTermsInFixedExpressionShape() {
        Random random = new Random(31);
        SearchEngine engine = new SearchEngine();
        for (int id = 0; id < 80; id++) {
            StringBuilder content = new StringBuilder();
            for (int word = 0; word < 8; word++) if (random.nextBoolean()) content.append("term").append(word).append(' ');
            engine.add(new Document(id, "!", content.toString()));
        }
        for (int i = 0; i < 100; i++) {
            String text = "(term" + random.nextInt(8) + " OR NOT term" + random.nextInt(8) + ") AND term" + random.nextInt(8);
            var query = QueryParser.parse(text);
            assertEquals(Benchmark.scan(engine.documents(), query), engine.matchingIds(query), text);
        }
    }
    @Test void preservesAllTokensFromOneUnicodeWord() {
        SearchEngine engine = new SearchEngine();
        engine.add(new Document(1, "!", "İSTANBUL"));
        engine.add(new Document(2, "!", "i"));

        assertEquals(Set.of(1), engine.matchingIds(QueryParser.parse("İSTANBUL")));
        assertEquals(Set.of(2), engine.matchingIds(QueryParser.parse("NOT İSTANBUL")));
        assertEquals(engine.search("i AND stanbul"), engine.search("İSTANBUL"));
        assertEquals(Set.of("i", "stanbul"), QueryParser.parse("İSTANBUL").positiveTerms());
        assertEquals(Set.of(), QueryParser.parse("NOT İSTANBUL").positiveTerms());
    }

    @Test void keepsUnicodeExpansionGroupedWithOtherOperators() {
        SearchEngine engine = new SearchEngine();
        engine.add(new Document(1, "!", "İSTANBUL"));
        engine.add(new Document(2, "!", "i"));
        engine.add(new Document(3, "!", "stanbul"));
        engine.add(new Document(4, "!", "other"));

        assertEquals(Set.of(1), engine.matchingIds(QueryParser.parse("(İSTANBUL)")));
        assertEquals(Set.of(2, 3, 4), engine.matchingIds(QueryParser.parse("NOT (İSTANBUL)")));
        assertEquals(Set.of(1), engine.matchingIds(QueryParser.parse("NOT NOT İSTANBUL")));
        assertEquals(Set.of(1), engine.matchingIds(QueryParser.parse("İSTANBUL AND i")));
        assertEquals(Set.of(1), engine.matchingIds(QueryParser.parse("i İSTANBUL")));
        assertEquals(Set.of(1, 4), engine.matchingIds(QueryParser.parse("İSTANBUL OR other")));
        assertEquals(Set.of(2), engine.matchingIds(QueryParser.parse("NOT İSTANBUL AND i")));
        assertEquals(Set.of(1, 4), engine.matchingIds(QueryParser.parse("other OR İSTANBUL AND i")));
        assertEquals(Set.of(2, 3), engine.matchingIds(QueryParser.parse("NOT (İSTANBUL OR other)")));
    }

    @Test void handlesUnicodeExpansionAtTheExistingCharacterLimit() {
        SearchEngine engine = new SearchEngine();
        engine.add(new Document(1, "!", "i"));
        assertEquals(Set.of(1), engine.matchingIds(QueryParser.parse("İ".repeat(4096))));
        assertEquals(Set.of(1), engine.matchingIds(QueryParser.parse("İ ".repeat(256).strip())));
        assertThrows(IllegalArgumentException.class, () -> QueryParser.parse("İ ".repeat(257).strip()));
    }
    @Test void handlesEmptyCorpusAndDuplicateIds() {
        SearchEngine engine = new SearchEngine();
        assertEquals(List.of(), engine.search("NOT java"));
        engine.add(new Document(0, "!", ""));
        assertThrows(IllegalArgumentException.class, () -> engine.add(new Document(0, "java", "")));
        assertEquals(1, engine.documentCount());
        assertEquals(List.of(), engine.search("java"));
    }
    @Test void groupsNegationAndTreatsLowercaseOperatorsAsWords() {
        assertEquals(Set.of(4), ids("NOT (java OR python)"));
        assertEquals(Set.of(1, 3, 4), ids("NOT (java AND python)"));
        SearchEngine engine = new SearchEngine();
        engine.add(new Document(9, "!", "and or not"));
        engine.add(new Document(2, "!", "and or not"));
        assertEquals(List.of(2, 9), engine.search("and or not").stream().map(r -> r.document().id()).toList());
    }
}
