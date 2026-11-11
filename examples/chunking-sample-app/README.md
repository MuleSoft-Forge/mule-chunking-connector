# Chunking connector sample application

A small Mule application with one flow for each feature of the chunking connector. It needs no external system,
credential or network service: its input data is bundled under `src/main/resources/data`, and each flow is started
by an HTTP request to `localhost`.

It depends on the connector version given by `chunking.connector.version` in `pom.xml`. To build against a local
build of the connector, install the connector first (`mvn clean install` in the repository root).

## Opening it

- **Anypoint Studio**: File > Import > Anypoint Studio > Anypoint Studio project from File System, and choose this
  directory.
- **Anypoint Code Builder**: open this directory as a folder; it is recognised as a Mule project.

## Running a flow

Run the application from the tool, then open `http://localhost:8081/<flow name>` in a browser. Each flow answers
with a JSON summary of what it saw, and logs each chunk at INFO.

Each flow also has an MUnit test in `src/test/munit/chunking-examples-test.xml`, which the tools can run one at a
time without starting the application.

| Flow | What it shows |
|---|---|
| `bare-operation` | The operation with no settings: 1 MB chunks, non-repeatable |
| `by-size` | Chunks of 256 bytes |
| `by-delimiter-default-newline` | Ten lines per chunk, the newline delimiter being the default |
| `by-delimiter-expression` | A delimiter that cannot be typed (the record separator, 0x1E), given as an expression |
| `short-chunk-max-chunk-size` | A chunk ended early by `maxChunkSize`, seen through `recordCount`, with no error |
| `streaming-non-repeatable` | Read once: a second pass sees no chunks |
| `streaming-sliding-window` | Both routes of a scatter-gather read every chunk, with `readers="2"`; each chunk is let go once both have read it |
| `streaming-in-memory` | Fully repeatable: two passes, at most 20 chunks in memory |
| `streaming-file-store` | 2 chunks in memory and the rest on disk, read by both routes of a scatter-gather |
| `error-invalid-chunk-size` | `CHUNKING:INVALID_CHUNK_SIZE` caught by type |
| `error-invalid-records-per-chunk` | `CHUNKING:INVALID_RECORDS_PER_CHUNK` caught by type |
| `error-invalid-delimiter` | `CHUNKING:INVALID_DELIMITER` caught by type |
| `error-invalid-parameter` | `CHUNKING:INVALID_PARAMETER` for an unknown encoding |
| `error-parent-type` | A handler for `CHUNKING:INVALID_PARAMETER` catching `CHUNKING:INVALID_CHUNK_SIZE` |
| `error-no-content` | `CHUNKING:READ_ERROR` when there is no content |
| `error-failure-while-reading` | A record larger than `maxChunkSize`, failing while `foreach` reads, handled with `ANY` |

## Building

`mvn clean package` builds the application and runs the MUnit tests.
