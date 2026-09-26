package com.astersearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import com.astersearch.search.SearchEngine;
import com.astersearch.search.SearchResult;
import com.astersearch.storage.SnapshotStore;
import com.astersearch.storage.TextImporter;

public final class App {
    private App() {}

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out, System.err));
    }

    public static int run(String[] args, InputStream input, PrintStream output, PrintStream errors) {
        try {
            if (args.length == 0 || args[0].equals("help")) {
                output.println("AsterSearch: index <folder> <snapshot> | search <snapshot> \"query\" [limit] | load <snapshot>");
                output.println("Operators: NOT > AND > OR; adjacent words mean AND. Use uppercase operators. load starts a query prompt; :quit exits.");
                return 0;
            }
            switch (args[0]) {
                case "index" -> {
                    require(args.length == 3, "index <folder> <snapshot>");
                    SearchEngine engine = TextImporter.load(Path.of(args[1]));
                    SnapshotStore.save(engine, Path.of(args[2]));
                    output.println("Indexed " + engine.documentCount() + " documents into " + args[2]);
                }
                case "search" -> {
                    require(args.length == 3 || args.length == 4, "search <snapshot> \"query\" [limit]");
                    int limit = args.length == 4 ? Integer.parseInt(args[3]) : 10;
                    require(limit > 0, "limit must be positive");
                    print(SnapshotStore.load(Path.of(args[1])), args[2], limit, output);
                }
                case "load" -> {
                    require(args.length == 2, "load <snapshot>");
                    SearchEngine engine = SnapshotStore.load(Path.of(args[1]));
                    output.println("Loaded " + engine.documentCount() + " documents. Enter queries or :quit.");
                    var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                    while (true) {
                        output.print("> ");
                        output.flush();
                        String query = reader.readLine();
                        if (query == null || query.equals(":quit")) {
                            break;
                        }
                        try {
                            print(engine, query, 10, output);
                        } catch (IllegalArgumentException error) {
                            errors.println("Error: " + error.getMessage());
                        }
                    }
                }
                default -> throw new IllegalArgumentException("Unknown command: " + args[0] + "; use help");
            }
            return 0;
        } catch (IOException | IllegalArgumentException error) {
            errors.println("Error: " + error.getMessage());
            return 2;
        }
    }

    private static void require(boolean condition, String usage) {
        if (!condition) {
            throw new IllegalArgumentException(usage);
        }
    }

    private static void print(SearchEngine engine, String query, int limit, PrintStream output) {
        List<SearchResult> results = engine.search(query);
        output.println(results.size() + " matches");
        // This limits display only: search has already scored and sorted every match.
        results.stream().limit(limit).forEach(result -> output.printf(Locale.ROOT, "%d\t%.6f\t%s%n",
                result.document().id(), result.score(), result.document().title().replaceAll("\\p{Cntrl}", " ")));
    }
}
