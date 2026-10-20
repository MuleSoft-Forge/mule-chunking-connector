# Changelog

## [0.2.1] - Unreleased

### Removed
- Dead code from v0.1.0 PagingProvider approach:
  - `ChunkPagingProvider.java`
  - `ChunkingConnection.java`
  - `ChunkingConnectionProvider.java`
  - `ChunkPagingProviderTest.java`
- Unused `defaultChunkSize` configuration parameter
- **Breaking:** `<chunking:config>` no longer accepts `<chunking:connection/>` or `defaultChunkSize`.
  Remove them from existing apps; `read-chunked` no longer needs a `config-ref`.

### Changed
- Cleaned up `ChunkingConfiguration.java` (removed unused annotations and fields)
- Updated deprecated constructor comment in `Chunk.java`

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
