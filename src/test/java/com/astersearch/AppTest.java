package com.astersearch;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppTest {
    @TempDir Path directory;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private int run(String input, String... args) {
        return App.run(args, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), new PrintStream(output), new PrintStream(errors));
    }
    @Test void indexesSearchesAndLoadsThroughCli() throws Exception {
        Path corpus = Files.createDirectory(directory.resolve("corpus"));
        Files.writeString(corpus.resolve("first.txt"), "java search java");
        Files.writeString(corpus.resolve("second.txt"), "python");
        String snapshot = directory.resolve("saved.ast").toString();
        assertEquals(0, run("", "index", corpus.toString(), snapshot));
        assertEquals(0, run("", "search", snapshot, "java NOT python", "1"));
        assertTrue(output.toString().contains("first.txt"));
        assertEquals(0, run("java OR\npython\n:quit\n", "load", snapshot));
        assertTrue(output.toString().contains("second.txt"));
        assertTrue(errors.toString().contains("Expected a word"));
    }
    @Test void reportsInvalidCommandsFilesAndLimits() {
        assertEquals(0, run("", "help"));
        assertEquals(2, run("", "unknown"));
        assertEquals(2, run("", "index"));
        assertEquals(2, run("", "load", directory.resolve("missing").toString()));
        assertEquals(2, run("", "search", "missing", "java", "0"));
        assertEquals(2, run("", "search", "missing", "java", "x"));
    }
}
