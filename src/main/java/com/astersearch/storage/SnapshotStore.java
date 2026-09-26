package com.astersearch.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import com.astersearch.model.Document;
import com.astersearch.search.SearchEngine;

/** Version 1: magic, version, payload length, SHA-256, documents and canonical index. */
public final class SnapshotStore {
    private static final int MAGIC = 0x41535452;
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 128 * 1024 * 1024;
    private SnapshotStore() {}

    public static void save(SearchEngine engine, Path destination) throws IOException {
        byte[] payload = payload(engine);
        if (payload.length > MAX_BYTES) {
            throw new IOException("Snapshot exceeds 128 MiB payload limit");
        }
        Path target = destination.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".aster-", ".tmp");
        try {
            try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(payload.length);
                output.write(digest(payload));
                output.write(payload);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                throw new IOException("Filesystem does not support atomic snapshot replacement", error);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static SearchEngine load(Path source) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid snapshot magic");
            }
            int version = input.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported snapshot version: " + version);
            }
            int size = input.readInt();
            if (size < 4 || size > MAX_BYTES) {
                throw new IOException("Invalid snapshot payload size");
            }
            byte[] checksum = new byte[32];
            input.readFully(checksum);
            byte[] payload = new byte[size];
            input.readFully(payload);
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing snapshot data");
            }
            if (!MessageDigest.isEqual(checksum, digest(payload))) {
                throw new IOException("Snapshot checksum mismatch");
            }
            return decode(payload);
        } catch (EOFException error) {
            throw new IOException("Incomplete snapshot", error);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid snapshot document: " + error.getMessage(), error);
        }
    }

    private static SearchEngine decode(byte[] payload) throws IOException {
        SearchEngine engine = new SearchEngine();
        try (var input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = input.readInt();
            if (count < 0 || count > payload.length / 12) {
                throw new IOException("Invalid snapshot document count");
            }
            int previousId = -1;
            for (int i = 0; i < count; i++) {
                int id = input.readInt();
                if (id <= previousId) {
                    throw new IOException("Snapshot IDs must be nonnegative, unique and sorted");
                }
                engine.add(new Document(id, readText(input), readText(input)));
                previousId = id;
            }
        }
        // Rebuilding also checks tokenizer compatibility, lengths, terms, frequencies and IDs.
        if (!Arrays.equals(payload, payload(engine))) {
            throw new IOException("Snapshot index data is inconsistent with documents");
        }
        return engine;
    }

    private static byte[] payload(SearchEngine engine) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(engine.documentCount());
            for (Document document : engine.documents()) {
                output.writeInt(document.id());
                writeText(output, document.title());
                writeText(output, document.content());
            }
            engine.writeIndex(output);
        }
        return bytes.toByteArray();
    }

    public static void writeText(DataOutput output, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > input.available()) {
            throw new IOException("Invalid snapshot string length");
        }
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Java runtime lacks SHA-256", error);
        }
    }
}
