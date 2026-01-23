# MuleSoft Chunking Connector

Stream large binary files into chunks for streaming processing in Mule with configurable memory and repeatability strategies.

## v0.2.0+ Streaming Strategies

Choose the right strategy based on your memory and repeatability needs:

### Non-Repeatable (Default) - Constant Memory
- **Memory**: O(chunkSize) - constant regardless of file size
- **Repeatability**: Single-pass only (foreach, but not scatter-gather)
- **Use case**: Large file streaming where you process each chunk once

### Sliding-Window - Bounded Repeatable
- **Memory**: O(maxCachedChunks × chunkSize) - hard limit enforced
- **Repeatability**: Multiple cursors (scatter-gather) with eviction
- **Use case**: Parallel processing of chunks with bounded memory
- **⚠️ Limitation**: Cursors with different consumption rates may cause seek errors if cache fills

### In-Memory - Full Buffering
- **Memory**: O(totalChunks × chunkSize) - ⚠️ OOM risk for large files
- **Repeatability**: Fully repeatable (Mule's repeatable-in-memory-stream)
- **Use case**: Small files where full buffering is acceptable

### File-Store - Disk-Backed (Mule EE)
- **Memory**: Bounded by buffer settings
- **Repeatability**: Fully repeatable with disk overflow
- **Use case**: Large files requiring repeatability with Mule EE

## Features

- **Multiple streaming strategies**: Choose memory vs. repeatability tradeoff
- **True constant memory**: Non-repeatable mode uses O(chunkSize) only
- **Lazy iteration**: Chunks read on-demand
- **Raw binary**: No encoding overhead - direct byte[] access
- **Sliding-window caching**: Bounded repeatability for parallel processing

## Installation

Add to your Mule project's `pom.xml`:

```xml
<dependency>
    <groupId>com.mulesoftforge</groupId>
    <artifactId>mule-chunking-connector</artifactId>
    <version>0.2.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

## Usage

### Example 1: Non-Repeatable (Constant Memory)

```xml
<!-- Read large file -->
<file:read path="/path/to/large-file.bin">
    <non-repeatable-stream />
</file:read>

<!-- Chunk into 100 MB pieces with non-repeatable strategy -->
<chunking:read-chunked chunkSize="#[100 * 1024 * 1024]">
    <chunking:streaming-strategy>
        <chunking:non-repeatable />
    </chunking:streaming-strategy>
</chunking:read-chunked>

<!-- Process each chunk once -->
<foreach>
    <logger message="Chunk #[payload.index]: #[payload.length] bytes"/>
    <s3:upload-part
        partNumber="#[payload.index + 1]"
        content="#[payload.data]"/>
</foreach>
```

### Example 2: Sliding-Window (Bounded Repeatable)

```xml
<!-- Read large file -->
<file:read path="/path/to/large-file.bin"/>

<!-- Chunk with sliding window (max 5 chunks cached) -->
<chunking:read-chunked chunkSize="#[100 * 1024 * 1024]">
    <chunking:streaming-strategy>
        <chunking:sliding-window maxCachedChunks="5" />
    </chunking:streaming-strategy>
</chunking:read-chunked>

<!-- Parallel processing with scatter-gather -->
<scatter-gather>
    <route>
        <foreach>
            <s3:upload-part content="#[payload.data]"/>
        </foreach>
    </route>
    <route>
        <foreach>
            <logger message="Chunk #[payload.index]"/>
        </foreach>
    </route>
</scatter-gather>
```

### Example 3: In-Memory (Fully Repeatable, Small Files)

```xml
<chunking:read-chunked chunkSize="1048576">
    <chunking:streaming-strategy>
        <chunking:in-memory
            initialBufferSize="100"
            maxBufferSize="500" />
    </chunking:streaming-strategy>
</chunking:read-chunked>
```

### Example 4: File-Store (Disk-Backed, Mule EE)

```xml
<chunking:read-chunked chunkSize="1048576">
    <chunking:streaming-strategy>
        <chunking:file-store
            maxInMemorySize="100"
            bufferUnit="KB" />
    </chunking:streaming-strategy>
</chunking:read-chunked>
```

### Best Practices

1. **Non-repeatable for large files**: Use for single-pass processing (e.g., upload to S3)
2. **Sliding-window for parallelism**: Use when you need scatter-gather with bounded memory
   - ⚠️ **Important**: All routes should consume chunks at similar rates
   - Avoid `batchSize` in foreach if routes have different batch sizes
   - If cache fills, oldest chunks are forcibly evicted (may cause seek errors)
3. **In-memory for small files**: Only use if total file size fits in heap
4. **Avoid sizeOf()**: Use `payload.length` instead of `sizeOf(payload.data)` to prevent holding references

### Sliding-Window Limitations

The sliding-window strategy enforces a **hard limit** on cache size. When cache exceeds `maxCachedChunks`:
- Throws **CHUNKING:CACHE_OVERFLOW** error with diagnostic information
- Error message includes cursor positions and cache size
- Indicates cursors are consuming at vastly different rates

**Solutions when CACHE_OVERFLOW occurs:**
1. **Increase `maxCachedChunks`** to accommodate the lag between cursors
2. **Balance consumption rates**: Ensure all scatter-gather routes consume at similar speeds
3. **Remove `batchSize` differences**: Don't mix different batch sizes in foreach
4. **Switch strategy**: Use `in-memory` or `file-store` for full repeatability

**Example of problematic configuration:**
```xml
<scatter-gather>
    <route>
        <foreach>...</foreach>  <!-- Fast: 1 chunk at a time -->
    </route>
    <route>
        <foreach batchSize="2">...</foreach>  <!-- Slow: waits for pairs -->
    </route>
</scatter-gather>
```

This will throw `CHUNKING:CACHE_OVERFLOW` because route 2 lags behind. Increase `maxCachedChunks` or remove `batchSize="2"`.

### Configuration

```xml
<chunking:config name="Chunking_Config">
    <chunking:connection/>
</chunking:config>
```

### Operation: read-chunked

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `chunkSize` | Integer | 1048576 (1MB) | Size of each chunk in bytes |
| `content` | InputStream | payload | Binary content to chunk |
| `streamingStrategy` | Strategy | non-repeatable | Controls memory and repeatability |

### Streaming Strategy Options

| Strategy | Memory | Repeatable | Parameters |
|----------|--------|------------|------------|
| `non-repeatable` | O(chunkSize) | No | None |
| `sliding-window` | O(maxCachedChunks × chunkSize) | Limited | `maxCachedChunks` (default: 10) |
| `in-memory` | O(totalSize) | Yes | `initialBufferSize`, `maxBufferSize` |
| `file-store` | Bounded | Yes | `maxInMemorySize`, `bufferUnit` |

### Chunk Properties

Each chunk has these properties:

| Property | Type | Description |
|----------|------|-------------|
| `payload.data` | `byte[]` | Raw binary chunk data |
| `payload.index` | `int` | 0-based chunk number |
| `payload.offset` | `long` | Byte position in source |
| `payload.length` | `int` | Bytes in this chunk |
| `payload.isFirst` | `boolean` | True for first chunk |
| `payload.isLast` | `boolean` | True for final chunk |

### DataWeave Examples

```dataweave
// Get chunk number (1-based for APIs)
payload.index + 1

// Check if processing last chunk
payload.isLast

```

## Error Handling

### Error Types

| Error Type | Description | Solutions |
|------------|-------------|-----------|
| `CHUNKING:READ_ERROR` | Failed to read from input stream | Check file permissions, stream availability |
| `CHUNKING:INVALID_CHUNK_SIZE` | Chunk size is invalid (≤ 0) | Use positive chunk size value |
| `CHUNKING:CACHE_OVERFLOW` | Sliding-window cache exceeded maxCachedChunks | Increase maxCachedChunks, balance cursor consumption rates, or switch strategy |

### Handling CACHE_OVERFLOW

```xml
<try>
    <chunking:read-chunked chunkSize="104857600">
        <chunking:streaming-strategy>
            <chunking:sliding-window maxCachedChunks="10" />
        </chunking:streaming-strategy>
    </chunking:read-chunked>
    <scatter-gather>
        <!-- Your routes -->
    </scatter-gather>

    <error-handler>
        <on-error-continue type="CHUNKING:CACHE_OVERFLOW">
            <logger level="ERROR" message="Cache overflow: #[error.description]"/>
            <!-- Fallback logic or increase maxCachedChunks -->
        </on-error-continue>
    </error-handler>
</try>
```

## Use Cases

- **S3 Multipart Upload**: Split large files for parallel upload
- **Streaming ETL**: Process large files without loading into memory
- **Checksum Calculation**: Hash files in chunks
- **Network Transfer**: Send large files in manageable pieces

## Known Issues

### DataWeave Cursor Leak in foreach (Mule 4.10.2)

**Issue**: DataWeave's `ArrayType.accepts()` opens cursors for type validation but never releases them, creating "zombie cursors" that remain at position 0.

**Impact**: When using sliding-window strategy with scatter-gather and foreach, zombie cursors block eviction by keeping minPosition at 0.

**Workaround**: The connector automatically detects and excludes DataWeave zombie cursors from eviction calculations (v0.2.0.6+). A warning is logged when detected.

**Details**: See [docs/dataweave-cursor-leak-report.md](docs/dataweave-cursor-leak-report.md) for complete technical analysis and reproduction steps.

**Status**: Reported to MuleSoft for Mule 4.10.2 / DataWeave 2.10.2.

## Requirements

- Mule Runtime 4.9.0 or later
- Java 17

## License

Apache License 2.0 - see [LICENSE](LICENSE) file.

## Contributing

Contributions welcome! Please see the [MuleSoft Forge contributing guide](https://github.com/MuleSoft-Forge/.github/blob/main/CONTRIBUTING.md).

## Links

- [GitHub Repository](https://github.com/MuleSoft-Forge/mule-chunking-connector)
- [MuleSoft Forge Documentation](https://docs.mulesoftforge.com/connectors/mule-chunking-connector/)
- [Report Issues](https://github.com/MuleSoft-Forge/mule-chunking-connector/issues)
