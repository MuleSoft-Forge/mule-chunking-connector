# MuleSoft Chunking Connector

Stream large binary files into iterable chunks for processing with `<foreach>`, maintaining constant memory regardless of file size.

## Features

- **True streaming**: O(chunkSize) memory, not O(fileSize)
- **PagingProvider integration**: Native Mule streaming support
- **Lazy iteration**: Chunks read on-demand via `<foreach>`
- **Raw binary**: No encoding overhead - direct byte[] access

## Installation

Add to your Mule project's `pom.xml`:

```xml
<dependency>
    <groupId>com.mulesoftforge</groupId>
    <artifactId>mule-chunking-connector</artifactId>
    <version>0.1.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

## Usage

### Basic Example

```xml
<!-- Read large file -->
<file:read path="/path/to/large-file.bin"/>

<!-- Chunk into pieces -->
<chunking:read-chunked chunkSize="5242880"/>

<!-- Process each chunk with constant memory -->
<foreach>
    <logger message="Chunk #[payload.index]: #[payload.length] bytes"/>

    <!-- Example: S3 multipart upload -->
    <s3:upload-part
        partNumber="#[payload.index + 1]"
        content="#[payload.data]"/>
</foreach>
```

### Configuration

```xml
<chunking:config name="Chunking_Config">
    <chunking:connection/>
</chunking:config>
```

### Operation: read-chunked

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `chunkSize` | Integer | 65536 (64KB) | Size of each chunk in bytes |
| `content` | InputStream | payload | Binary content to chunk |

### Chunk Properties

Each chunk in the `<foreach>` loop has these properties:

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

// Calculate progress percentage (if total known)
(payload.offset + payload.length) / vars.totalSize * 100
```

## Memory Usage

Memory consumption: **chunkSize + ~1 byte overhead**

| Chunk Size | Memory Used | File Size | Chunks |
|------------|-------------|-----------|--------|
| 5 MB | ~5 MB | 500 MB | 100 |
| 5 MB | ~5 MB | 5 GB | 1,000 |
| 10 KB | ~10 KB | 665 MB | 66,500 |

The connector was tested with a 665 MB file producing 66,500 chunks with constant memory usage.

## Use Cases

- **S3 Multipart Upload**: Split large files for parallel upload
- **Streaming ETL**: Process large files without loading into memory
- **Checksum Calculation**: Hash files in chunks
- **Network Transfer**: Send large files in manageable pieces

## Requirements

- Mule Runtime 4.4.0 or later
- Java 8 or later

## License

Apache License 2.0 - see [LICENSE](LICENSE) file.

## Contributing

Contributions welcome! Please see the [MuleSoft Forge contributing guide](https://github.com/MuleSoft-Forge/.github/blob/main/CONTRIBUTING.md).

## Links

- [GitHub Repository](https://github.com/MuleSoft-Forge/mule-chunking-connector)
- [MuleSoft Forge Documentation](https://docs.mulesoftforge.com/connectors/mule-chunking-connector/)
- [Report Issues](https://github.com/MuleSoft-Forge/mule-chunking-connector/issues)
