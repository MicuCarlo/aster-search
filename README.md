# AsterSearch

AsterSearch is a small search engine built with Java 21 and Maven. Give it a folder of text files, and it builds an index you can search with queries like `java AND NOT python`. It ranks matches with BM25 and saves snapshots so you can load your documents again later.

Everything runs on Java collections, with no search library or other runtime dependencies. The tests use JUnit. If you want to follow the code, the [architecture guide](docs/ARCHITECTURE.md) walks through what happens when you add a document, search and save.

## Try it

You'll need JDK 21 and Maven on your PATH. From the project folder, run this in PowerShell:

```powershell
java -version
mvn -version
mvn --batch-mode clean verify

# Create a small corpus (Set-Content creates/overwrites these example files).
New-Item -ItemType Directory -Force target/demo | Out-Null
Set-Content -Encoding UTF8 target/demo/java.txt 'Java powers this search engine. Java collections store the index.'
Set-Content -Encoding UTF8 target/demo/python.txt 'Python is another programming language.'

java -jar target/aster-search-0.1.0-SNAPSHOT.jar index target/demo target/demo.ast
java -jar target/aster-search-0.1.0-SNAPSHOT.jar search target/demo.ast 'java AND NOT python'
java -jar target/aster-search-0.1.0-SNAPSHOT.jar search target/demo.ast '(java OR python) AND language' 5
java -jar target/aster-search-0.1.0-SNAPSHOT.jar load target/demo.ast
# At the prompt, enter a query; :quit or end of input exits.
```

That builds the JAR, creates two example files and saves them in a snapshot. The two `search` commands try different queries. The final `load` command opens a prompt where you can keep searching without loading the snapshot again.

These are the commands you'll use:

| Command | What it does |
| --- | --- |
| `index <folder> <snapshot>` | Imports a fresh collection and saves it. The snapshot's parent folder must exist. An existing snapshot is replaced atomically. |
| `search <snapshot> "query" [limit]` | Loads the snapshot, shows the total match count, then prints each displayed result's ID, score and title. The default limit is 10. |
| `load <snapshot>` | Loads once and opens an interactive search prompt. Enter `:quit` or end the input to leave. |
| `help` | Prints the command syntax. |

If something goes wrong with a file, query or argument, you'll see `Error: ...` and the command exits with code 2. At the interactive prompt, a bad query lets you try again without closing the session.

## Write a query

Start with a word, add AND or OR to combine terms, and use NOT to leave something out:

| Query | Meaning |
| --- | --- |
| `java` | Contains the term java |
| `java search` | Contains both terms (implicit AND) |
| `java OR python` | Contains either or both |
| `java NOT python` | Contains java and excludes python |
| `NOT java` | All documents without java, including tokenless documents |
| `(java OR python) AND search` | Group before intersection |

NOT binds first, then AND, then OR. AND and OR each associate from left to right. Parentheses let you choose a different grouping. Putting words, groups or NOT expressions next to each other also means AND, so `java search` works like `java AND search`.

Use uppercase for operators. Lowercase `and`, `or` and `not` are ordinary search words. The words you're looking for otherwise ignore case.

The query language is deliberately small: letters, numbers, whitespace and parentheses. Quotes, hyphens and other punctuation are errors, as are empty queries, missing operands and unmatched parentheses. Phrase search isn't available. A query can have up to 4,096 characters and 256 lexical tokens, counting operators and parentheses.

There's one Unicode detail worth understanding. Lowercasing `İSTANBUL` produces an `i`, a combining dot and `stanbul`. The tokenizer splits at the dot, giving `[i, stanbul]`. The parser keeps both tokens together as `(i AND stanbul)`. That also means `NOT İSTANBUL` is `NOT (i AND stanbul)`: a document with just `i` still matches, because it doesn't contain both words.

## What gets searched

The importer looks through your folder and its subfolders for `.txt` files, ignoring case in the extension. Files must be valid UTF-8. A leading UTF-8 BOM is removed, symbolic links are skipped, and linked folders aren't followed. Empty files and folders are fine. If a file can't be decoded, the import stops before writing a snapshot.

The title comes from the relative path, including the extension, rather than the first line of text. For example, a file might be titled `notes/java.txt`. Folder separators become slashes. Paths are sorted using Java's String order, which distinguishes uppercase and lowercase letters, then given IDs 0, 1, 2, ... . The same paths get the same IDs, but adding or removing a file can shift the IDs of others.

Both the title and content count towards matching and ranking, with equal weight. So filenames are searchable, and `txt` appears in every imported title.

The tokenizer lowercases with `Locale.ROOT` and splits wherever a character isn't a Unicode letter or number. Document punctuation therefore separates words, even though query punctuation is rejected. There is no stemming, stopword removal or normalization between composed and decomposed Unicode forms.

## How results are ordered

First, the Boolean query finds the matching documents. Then BM25 scores them using `k1 = 1.2`, `b = 0.75`, natural logarithms and positive IDF. Higher scores come first. Ties go to the smaller document ID.

A query term contributes once, even if you repeat it. Terms under an odd number of NOT operators don't score; an even number restores their contribution. A query made entirely of negative terms gives every match a score of zero.

Scoring looks at positive terms across the whole query. A term can contribute even when it belongs to a different OR branch from the one that let the document match. The score therefore doesn't tell you which branch matched. Terms are summed in sorted order to keep rounding consistent.

The display limit only changes what you see. Even if you ask for five results, the engine scores and sorts every match first.

## Save your documents for later

A snapshot contains the documents, lengths and postings, plus magic bytes, format version 1 and a SHA-256 checksum. Loading rebuilds the index and compares it with the saved data. This catches truncated files, corruption, unsupported versions and inconsistent index data, but it also means loading pays the cost of indexing again.

Saving writes a temporary file in the destination folder and replaces the snapshot with an atomic move. If the filesystem can't make that move, saving fails. The payload can be at most 128 MiB, and the memory needed to save or load it is considerably larger. The checksum catches accidental damage; it doesn't protect against someone who can rewrite both the file and checksum.

## Tests and timings

`mvn --batch-mode clean verify` compiles the project, runs the tests and builds the executable JAR. GitHub Actions is configured to run it on Windows and Linux with Java 21. A successful remote run hasn't been confirmed.

To try the benchmark after building:

```powershell
# Run after building. The corpus directory must not already exist.
java -Xms512m -Xmx2g -cp target/classes com.astersearch.benchmark.Benchmark target/benchmark-corpus
```

It creates 10,000 synthetic text files from a fixed seed. Use a new folder for another run, or run `mvn clean verify` to remove generated files under `target`.

The benchmark compares the index with two scans. One tokenizes every document again for each query; the other keeps a word set for each document and reuses it. It also times importing and indexing, preparing those sets, ranked searches in memory and snapshot loading.

Those timers answer different questions. Matching excludes parsing, ranking and I/O. Ranked search adds parsing, BM25 and sorting every match, but excludes loading and CLI output. Snapshot loading covers reading, validation and rebuilding with a warm OS cache. None measures a complete CLI request. The [benchmark writeup](docs/BENCHMARK.md) explains the measurements, and the [raw output](docs/benchmark-results.txt) has every sample.

## What's still missing

The engine keeps its data in memory and uses one thread. You can't update or delete individual documents yet. A REST API and UI, phrase search using word positions, trie autocomplete, spelling correction using edit distance, caching and parallel indexing are possible next steps.
