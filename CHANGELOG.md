# Changelog

## [1.0.0] - 2026-11-11

1.0.0 includes the changes made after 0.2.0 that were never released as 0.2.1. Since 1.0.0-beta1 the one change is
the connector's icon.

### Breaking changes: what a 0.2.0 application must change

- **`chunkSize` moves into a `by-size` element.** It is no longer an attribute of `read-chunked`. Write
  `<chunking:read-chunked><chunking:chunking-strategy><chunking:by-size chunkSize="..."/></chunking:chunking-strategy></chunking:read-chunked>`.
  Without a `chunking-strategy` the content is cut by size into 1 MB chunks, as before.
- **The global configuration is gone from use.** `<chunking:config>` no longer accepts `<chunking:connection/>` or
  `defaultChunkSize`; remove them. `read-chunked` needs no `config-ref`.
- **Invalid values fail.** A `chunkSize` of zero or less used to be replaced silently with 1 MB; it now fails with
  `CHUNKING:INVALID_CHUNK_SIZE`. Every streaming strategy setting must be a positive number; zero or less fails with
  `CHUNKING:INVALID_PARAMETER`, and zero no longer means "no limit" anywhere.
- **Streaming strategy settings are renamed** to one name for chunks held in memory, and now take effect:
  - `sliding-window`: `maxCachedChunks` is now `maxInMemoryChunks`, default 3, now a hard limit.
  - `in-memory`: `maxBufferSize` is now `maxInMemoryChunks`, default 10. `initialBufferSize` and
    `bufferSizeIncrement` are removed; remove them from the element.
  - `file-store`: `maxInMemoryInstances` is now `maxInMemoryChunks`, default 10.
- **`in-memory` and `file-store` now do what their settings say.** In 0.2.0 both handed the chunks to the runtime's
  default repeatable streaming and ignored their settings. `in-memory` now fails the read when it would hold more
  than `maxInMemoryChunks` chunks (10 by default, about 10 MB at 1 MB chunks); an application that reads more chunks
  than that must raise the setting or use `file-store`. `file-store` now keeps `maxInMemoryChunks` chunks in memory
  and writes the rest to a temporary file.
- **The sliding window keeps chunks for readers that start a little later, and needs `readers` for more.** In
  0.2.0 a chunk was let go as soon as every reader reading at that moment had passed it, so a reader that started
  a moment later, such as the second route of a `scatter-gather`, found the chunks gone. Now, with `readers` not
  set, chunks are kept up to `maxInMemoryChunks` and only the oldest that every reader so far has read are then let
  go; with `readers` set, a chunk is let go once that many readers have read it. A flow whose readers start further
  apart than `maxInMemoryChunks` chunks must set `readers`. A read that would hold more than `maxInMemoryChunks`
  chunks now fails instead of growing.
- **Error types.** `CHUNKING:CACHE_OVERFLOW` is no longer declared; it was never raised. A failure while the chunks
  are being read, such as the source failing or a streaming strategy's limit being reached, reaches the flow as
  `MULE:UNKNOWN` inside `foreach` (`MULE:EXPRESSION` inside a DataWeave expression). A handler for
  `CHUNKING:CACHE_OVERFLOW` must be replaced by one for `ANY` around the `foreach`, since an error handler cannot
  name `MULE:UNKNOWN`. `CHUNKING:READ_ERROR` is raised only when there is no content.
- **The chunk's flags are read as `payload.first` and `payload.last`.** The 0.2.0 README showed
  `payload.isFirst` and `payload.isLast`, which give `null` in a flow.
- **The `Chunk` Java type** is now read-only: its public constructor and `update(...)` method are gone.

### Added

- Chunking by delimiter: `<chunking:by-delimiter>` cuts the content into chunks of `recordsPerChunk` delimited records,
  with `delimiter` (text or an expression; default a newline), `encoding` (default UTF-8) and `maxChunkSize`
  (default 1 MB). The maximum chunk size never cuts a record: a chunk ends early instead, and a single record larger
  than it fails.
- `recordCount` on each chunk: the number of records it holds when chunking by delimiter; empty when chunking by size.
- `readers` on `sliding-window`: how many readers will read the chunks. Optional.
- `CHUNKING:INVALID_PARAMETER`, parent of `CHUNKING:INVALID_CHUNK_SIZE`, and the new
  `CHUNKING:INVALID_RECORDS_PER_CHUNK` and `CHUNKING:INVALID_DELIMITER`. It is also raised for an unrecognised
  `encoding` and for a streaming strategy setting that is not positive.
- An icon of the connector's own: equal slanted chunks with a shorter last one, white on a teal circle.

### Changed

- What `sliding-window`, `in-memory` and `file-store` hold is released when the event that read the chunks ends,
  and `file-store` deletes its temporary file then. A read after that fails rather than finding no chunks.
- `file-store` no longer needs the Mule Enterprise runtime.
- The sliding window counts as a reader only what has read a chunk, so a cursor DataWeave opens to check the
  chunks' type, and never reads, no longer holds chunks back. The connector no longer inspects stack traces to
  recognise that cursor, and no longer logs a warning for it.
- The connector logs nothing at INFO. The line each invocation writes with its settings is at DEBUG, with the
  detail of each chunk; 0.2.0 wrote it at INFO.

### Fixed

- Under the non-repeatable strategy, a chunk shorter than the chunk size (such as the last) had `data` holding bytes
  left over from the chunk before. Every chunk's `data` now holds exactly its bytes.
- A failure of the source while `foreach` read the chunks under the non-repeatable strategy left the flow waiting
  forever. It is now raised to the flow.
- A failure while checking for more data was taken for the end of the data, so the chunks stopped early with no
  error. It is now raised.
- Under the sliding window, the second route of a `scatter-gather` could find the chunks gone because it started a
  moment after the first. See the breaking change above.

### Removed

- Unused code from the 0.1.0 paging design: `ChunkPagingProvider`, `ChunkingConnection`,
  `ChunkingConnectionProvider` and their test.
- `initialBufferSize` and `bufferSizeIncrement` on `in-memory` (see Breaking changes).

## [1.0.0-beta1] - 2026-11-11

A preview of 1.0.0, published so that it can be tried before 1.0.0 is released. It contains everything listed under
1.0.0 above, including every breaking change a 0.2.0 application must act on. Feedback is wanted: please
[open an issue](https://github.com/MuleSoft-Forge/mule-chunking-connector/issues) with what you find. 1.0.0 may
differ from this beta.

## [0.2.0] - 2026-01-23

### Added
- Four streaming strategies via `@SubTypeMapping` pattern:
  - `<chunking:non-repeatable />` - O(chunkSize) constant memory
  - `<chunking:sliding-window maxCachedChunks="N" />` - Bounded repeatable
  - `<chunking:in-memory />` - Mule's repeatable buffer (⚠️ OOM risk)
  - `<chunking:file-store />` - Mule EE file-backed buffer
- `SlidingWindowCursorIteratorProvider` with automatic eviction
- DataWeave zombie cursor detection and workaround
- Buffer reuse mode in `ChunkIterator` for constant memory

### Fixed
- OOM with scatter-gather (now works with sliding-window strategy)

## [0.1.0] - 2026-01-19

### Added
- Initial release
- `read-chunked` operation using PagingProvider
- Basic chunking of binary streams
