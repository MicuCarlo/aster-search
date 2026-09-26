package com.astersearch.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import com.astersearch.model.Document;
import com.astersearch.search.SearchEngine;

public final class TextImporter {
    private TextImporter() {}

    public static SearchEngine load(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a directory: " + directory);
        }
        List<Path> files;
        try (var paths = Files.walk(directory)) {
            files = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> title(directory, path))).toList();
        } catch (UncheckedIOException error) {
            throw new IOException("Cannot traverse " + directory + ": " + error.getCause().getMessage(), error.getCause());
        }
        SearchEngine engine = new SearchEngine();
        for (int id = 0; id < files.size(); id++) {
            Path file = files.get(id);
            try {
                String content = Files.readString(file); // Strict UTF-8 decoding.
                if (content.startsWith("\uFEFF")) {
                    content = content.substring(1);
                }
                engine.add(new Document(id, title(directory, file), content));
            } catch (IOException error) {
                throw new IOException("Cannot import " + file + ": " + error.getMessage(), error);
            }
        }
        return engine;
    }

    private static String title(Path directory, Path file) {
        return directory.relativize(file).toString().replace('\\', '/');
    }
}
