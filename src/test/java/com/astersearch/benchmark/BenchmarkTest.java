package com.astersearch.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.astersearch.storage.TextImporter;
import com.astersearch.model.Document;
import com.astersearch.query.QueryParser;
import com.astersearch.search.SearchEngine;
import java.util.List;
import java.util.Set;

class BenchmarkTest {
    @TempDir Path directory;
    @Test void generatesRepeatableFilesAndRefusesToOverwrite() throws Exception {
        Path first = directory.resolve("first");
        Path second = directory.resolve("second");
        Benchmark.generate(first, 20);
        Benchmark.generate(second, 20);
        try (var files = Files.list(first)) {
            for (Path file : files.toList()) assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(second.resolve(file.getFileName())));
        }
        assertEquals(20, TextImporter.load(first).documentCount());
        assertThrows(IOException.class, () -> Benchmark.generate(first, 20));
    }

    @Test void matchingBaselinesAgreeForTitlesUnicodeNegationAndEmptyDocuments() {
        List<Document> documents = List.of(
                new Document(7, "Title", "İSTANBUL"),
                new Document(2, "!", "i"),
                new Document(15, "!", ""));
        SearchEngine engine = new SearchEngine();
        documents.forEach(engine::add);
        var documentWords = Benchmark.pretokenize(documents);

        assertEquals(Set.of("title", "i", "stanbul"), documentWords.get(7));
        assertEquals(Set.of(), documentWords.get(15));
        for (String text : List.of("title", "İSTANBUL", "NOT İSTANBUL", "i AND title", "title OR NOT i")) {
            var query = QueryParser.parse(text);
            assertEquals(engine.matchingIds(query), Benchmark.scan(documents, query), text);
            assertEquals(engine.matchingIds(query), Benchmark.scanPretokenized(documentWords, query), text);
        }
        assertEquals(Set.of(2, 15), Benchmark.scanPretokenized(documentWords, QueryParser.parse("NOT İSTANBUL")));
    }
}
