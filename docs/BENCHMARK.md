# How fast is it?

The benchmark tries the index and two scanning approaches on the same 10,000 generated documents. It also times a full ranked search in memory and loading a saved snapshot. These are separate measurements because they do different amounts of work.

You can find every sample in the [expanded run's raw output](benchmark-results.txt). The results below come from that recorded run.

## Start with the query results

All three matching methods returned the same document IDs for each query. Ranked search returned those IDs too. Here are the median times for finding matches, without parsing, scoring or sorting:

| Query | Matches | Indexed ms | Retokenizing scan ms | Pretokenized scan ms |
| --- | ---: | ---: | ---: | ---: |
| `rare` | 100 | 0.387 | 231.971 | 1.677 |
| `word42 word73` | 121 | 0.689 | 198.421 | 1.287 |
| `topic3 OR topic7` | 1,000 | 0.375 | 217.745 | 1.275 |
| `common AND NOT topic5` | 9,500 | 0.919 | 184.853 | 2.616 |
| `(topic1 OR topic2) AND common` | 1,000 | 0.539 | 175.606 | 1.518 |
| `NOT common` | 0 | 0.758 | 160.941 | 1.075 |

The index looks up postings. The retokenizing scan reads the documents already in memory and builds a temporary word set for each one on every query. The pretokenized scan does that preparation once and reuses the sets. It still visits every document's set for each query.

The next table measures more work: a call to `SearchEngine.search(String)`, from parsing the query to returning the complete sorted result list.

| Query | Matches | Ranked search ms |
| --- | ---: | ---: |
| `rare` | 100 | 0.692 |
| `word42 word73` | 121 | 0.826 |
| `topic3 OR topic7` | 1,000 | 1.001 |
| `common AND NOT topic5` | 9,500 | 2.287 |
| `(topic1 OR topic2) AND common` | 1,000 | 0.963 |
| `NOT common` | 0 | 0.849 |

That includes collecting positive terms, indexed matching, looking up documents, calculating BM25, creating results and sorting every match. It doesn't include importing documents, loading a snapshot, starting the JVM or formatting and printing CLI output.

Asking the CLI to show fewer results doesn't reduce this work. The display limit is applied after the engine has scored and sorted all matches.

## The preparation has a cost too

The index and pretokenized scan both do work before a query starts. Here's what the run recorded:

| Recorded quantity | Value |
| --- | ---: |
| Import and index construction, one observation | 2,220.403 ms |
| Pretokenized scan preprocessing, one observation | 342.370 ms |
| Retained word memberships across document sets | 1,171,562 |
| Snapshot file size | 19,440,705 bytes |

The import and index timer covers `TextImporter.load`: finding and sorting paths, reading UTF-8 files, creating documents and building the index. It stops before exporting the ordered document list. Generating the corpus, preparing the scan and saving a snapshot are outside that timer. The files have just been written, so the OS cache can help with reading them.

Preparing the pretokenized scan means tokenizing each document's title and content, then keeping a word set for each ID in a `LinkedHashMap`. The recorded preprocessing time covers that step using documents already in memory.

The 1,171,562 word memberships are a count, not a memory measurement. A word counts once in each document that contains it, so this isn't the number of unique words across the collection either. The map, sets and token strings stay in memory alongside the engine and document list. The run didn't measure retained or peak heap usage. Likewise, the file sizes here describe bytes on disk, not the memory needed to work with them.

## Loading a snapshot

After the query measurements, the benchmark saves a snapshot and loads it again. It compares the documents and the exact ranked results for all six queries before timing further loads. Saving and those comparison checks aren't timed.

The load timer wraps `SnapshotStore.load`. It includes opening, reading and closing the file, checking the envelope, length and checksum, and decoding the documents as strict UTF-8. Then it rebuilds the index, serializes it in a fixed order, including sorting, and compares every payload byte. The saved postings are checked against that rebuilt index rather than installed directly.

| Measurement | Median ms | Seven measured samples, ms |
| --- | ---: | --- |
| Snapshot load | 642.965 | 619.519801, 600.6575, 673.1619, 628.6924, 691.3517, 735.902099, 642.964901 |

This uses a **warm OS cache**: the snapshot has just been saved and repeatedly loaded. It doesn't measure a first read from cold storage. There are three warmup loads, followed by seven measured loads. Each loaded engine is assigned to a volatile reference after timing stops.

Loading excludes snapshot saving, generating or importing the original corpus, ranked searches, comparison checks outside the loader, JVM startup and CLI output. Don't add this median to a query median and call the sum a measured CLI request. A complete CLI request wasn't timed.

## How the query timers work

Before any query timing starts, the benchmark parses all six queries and checks that the three matching methods and ranked search return the same IDs. That gives us agreement on these queries, but doesn't independently prove the shared parser is correct or tell us whether the ranking is useful.

Each query gets three warmup rounds and seven measured rounds. Every round runs each of the four methods once. Their base order is indexed matching, retokenizing scan, pretokenized scan and ranked search. The first method rotates with `(round + offset) % 4`, making ranked search first in the first measured round. The rotation starts over for each query; the queries themselves stay in the order shown in the tables. This is a fixed rotation, not random ordering.

Elapsed time comes from `System.nanoTime`. After the timer stops, the complete result is assigned to a volatile reference. The seven measured samples stay in execution order in the output. A sorted copy supplies the median, which is the fourth value and is printed to three decimal places. The warmup samples are discarded.

For matching alone, each method receives an already parsed query and returns a set of IDs. The timer excludes parsing, file reading, importing and indexing, scan preparation, snapshot loading, BM25, sorting and CLI output. Indexed matching does include copying the full set of document IDs, even for a query without NOT.

## Recreate the data and try it yourself

`Benchmark.generate` creates files `doc00000.txt` through `doc09999.txt` with `java.util.Random` seed `20260925`. Each body contains `common`, one of 20 evenly assigned `topicN` terms, and `rare` in every hundredth document. It then adds 120 draws from `word0` through `word999` and a final LF.

That gives each body 122 or 123 tokens. Titles contribute filename tokens too, bringing indexed lengths to 124 or 125. The UTF-8 body files total **9,612,940 bytes**. Concatenating their contents in filename order gives this SHA-256:

```text
f1c8f6114225d7cd65b2bd3b0105945994532c0d81700f13ff954e68badec0ed
```

The repository contains the generator so you can recreate the files locally. From the project folder in PowerShell, run:

```powershell
mvn --batch-mode clean verify
java -Xms512m -Xmx2g -cp target/classes com.astersearch.benchmark.Benchmark target/benchmark-corpus
```

The output folder must not already exist. Pick a new name to keep an earlier run, or clean `target` before trying again. Corpus generation isn't part of the import and index measurement.

## Which machine and code produced these numbers?

The raw output reports OpenJDK `21.0.12.1+1-LTS`, OpenJDK 64-Bit Server VM and Windows 11 `10.0` amd64. Java saw 12 available processors and a maximum heap of 2,147,483,648 bytes. It doesn't record the CPU model or physical memory.

It also doesn't contain a timestamp or source revision. File timestamps put the run on 2026-09-25, but some application and storage files have later timestamps. The current benchmark agrees with the recorded methods, queries, warmups, sample counts and output, and inspection found no difference in what those timers cover. Without a source hash, though, we can't confirm that it is exactly the code used for the run. These numbers belong to that recorded run rather than a new performance check of the current tree.


## How much should you read into this?

These are short, generated documents with a synthetic vocabulary and similar lengths. Nobody has labelled which results should be most relevant, so the timings can't tell us how good the ranking is.

The run used one JVM process, three warmup rounds and seven samples per method and query. There were no separate JVM forks, CPU isolation or statistical confidence intervals. JIT compilation, garbage collection and other desktop activity can all affect the numbers; the raw samples show that variation.

The results don't establish a universal speedup or predict production throughput, p95/p99 latency, memory efficiency, startup from cold storage or performance on much larger collections. Useful next measurements would include real documents, multiple JVM forks, JMH, memory profiling and complete CLI requests. The current implementation still rebuilds the index when loading and scores and sorts every match before applying the display limit.
