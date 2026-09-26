package com.astersearch.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.astersearch.model.Document;
import com.astersearch.search.SearchEngine;

class StorageTest {
    @TempDir Path directory;
    @Test void importsRecursivelyInPathOrderWithUtf8AndBom() throws Exception {
        Files.createDirectory(directory.resolve("nested"));
        Files.writeString(directory.resolve("z.TXT"), "\uFEFFcafé");
        Files.writeString(directory.resolve("nested/a.txt"), "java");
        Files.writeString(directory.resolve("ignored.md"), "ignored");
        var engine = TextImporter.load(directory);
        assertEquals(List.of(new Document(0, "nested/a.txt", "java"), new Document(1, "z.TXT", "café")), engine.documents());
        assertEquals(engine.documents(), TextImporter.load(directory).documents());
        assertEquals(1, engine.search("café").size());
    }
    @Test void rejectsMissingDirectoryAndMalformedUtf8() throws Exception {
        assertThrows(IOException.class, () -> TextImporter.load(directory.resolve("absent")));
        Files.write(directory.resolve("bad.txt"), new byte[]{(byte) 0xc3, 0x28});
        assertThrows(IOException.class, () -> TextImporter.load(directory));
    }
    @Test void roundTripsDocumentsRankingAndDeterministicBytes() throws Exception {
        SearchEngine engine = new SearchEngine();
        engine.add(new Document(7, "Café", "java java"));
        engine.add(new Document(2, "!", ""));
        Path file = directory.resolve("index.ast");
        SnapshotStore.save(engine, file);
        byte[] first = Files.readAllBytes(file);
        SearchEngine loaded = SnapshotStore.load(file);
        assertEquals(engine.documents(), loaded.documents());
        assertEquals(engine.search("java OR NOT missing"), loaded.search("java OR NOT missing"));
        SnapshotStore.save(loaded, file);
        assertArrayEquals(first, Files.readAllBytes(file));
    }
    @Test void roundTripsEmptyCorpus() throws Exception {
        Path file = directory.resolve("empty.ast");
        SnapshotStore.save(new SearchEngine(), file);
        assertEquals(0, SnapshotStore.load(file).documentCount());
    }
    @Test void rejectsTruncationCorruptionVersionTrailingDataAndInconsistentIndex() throws Exception {
        SearchEngine engine = new SearchEngine();
        engine.add(new Document(0, "Java", "java"));
        Path file = directory.resolve("index.ast");
        SnapshotStore.save(engine, file);
        byte[] good = Files.readAllBytes(file);
        for (int size : new int[]{0, 3, 12, 43, good.length - 1}) {
            Files.write(file, Arrays.copyOf(good, size));
            assertThrows(IOException.class, () -> SnapshotStore.load(file));
        }
        for (int offset : new int[]{0, 7, 11, 44, good.length - 1}) {
            byte[] bad = good.clone(); bad[offset] ^= 1;
            Files.write(file, bad);
            assertThrows(IOException.class, () -> SnapshotStore.load(file));
        }
        Files.write(file, Arrays.copyOf(good, good.length + 1));
        assertThrows(IOException.class, () -> SnapshotStore.load(file));
        byte[] bad = good.clone(); bad[bad.length - 1] ^= 1;
        byte[] checksum = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOfRange(bad, 44, bad.length));
        System.arraycopy(checksum, 0, bad, 12, 32);
        Files.write(file, bad);
        assertTrue(assertThrows(IOException.class, () -> SnapshotStore.load(file)).getMessage().contains("inconsistent"));
    }
}
