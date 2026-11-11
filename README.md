# MuleSoft Chunking Connector

Reads content as a sequence of chunks, so that a flow can process content of any size one chunk at a time with
`foreach`. Chunks are cut either by size, a number of bytes each, or by delimiter, a number of delimited records
each, so that every chunk of a delimited file can be processed on its own.

## Installation

**1.0.0-beta1 is a preview of 1.0.0.** It contains everything this README describes and everything the 1.0.0
entry of the [changelog](CHANGELOG.md) lists. It is published so that it can be tried before 1.0.0 is released:
please try it and tell us what you find by
[opening an issue](https://github.com/MuleSoft-Forge/mule-chunking-connector/issues). 1.0.0 may differ from it.
The latest release that is not a preview is 0.2.0, which this README does not describe; see
[Upgrading from 0.2.0](#upgrading-from-020).

Add the connector to your Mule application's `pom.xml`:

```xml
<dependency>
    <groupId>com.mulesoftforge</groupId>
    <artifactId>mule-chunking-connector</artifactId>
    <version>1.0.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

Requirements: Mule runtime 4.9.6 or later, Java 17. The connector needs no global configuration element.

## Quick start

With no settings, `read-chunked` cuts the message payload into chunks of 1 MB, and `foreach` processes them one at a
time:

```xml
<chunking:read-chunked/>
<foreach>
    <logger level="INFO" message="#['Chunk $(payload.index): $(payload.length) bytes, last: $(payload.last)']"/>
</foreach>
```

The operation has two optional child elements: `chunking-strategy`, which says how the content is cut into chunks,
and `streaming-strategy`, which says how the chunks are held. In Anypoint Studio a strategy left as "None", and in
Anypoint Code Builder one whose box is left unticked, means the default: by size in chunks of 1 MB, and
non-repeatable.

## The chunk

Each chunk has these properties:

| Property | Type | Description |
|---|---|---|
| `payload.data` | binary (`byte[]`) | Exactly the chunk's bytes |
| `payload.index` | number | The chunk's position in the sequence, counting from 0 |
| `payload.offset` | number | The position of the chunk's first byte in the content, counting from 0 |
| `payload.length` | number | The number of bytes in the chunk |
| `payload.first` | boolean | `true` for the first chunk |
| `payload.last` | boolean | `true` for the last chunk, including when the content ends exactly at the end of this chunk |
| `payload.recordCount` | number | When chunking by delimiter, the number of records the chunk holds; empty (`null`) when chunking by size |

Every chunk is an exact slice of the content: the chunks' `data`, taken in order, reproduce the content byte for
byte. Empty content produces no chunks.

## Chunking strategies

### By size

`by-size` cuts the content into chunks of `chunkSize` bytes; the last chunk holds what is left. This is what happens
when no chunking strategy is given.

```xml
<chunking:read-chunked>
    <chunking:chunking-strategy>
        <chunking:by-size chunkSize="65536"/>
    </chunking:chunking-strategy>
</chunking:read-chunked>
<foreach>
    <logger level="INFO" message="#['Chunk $(payload.index): $(payload.length) bytes from byte $(payload.offset)']"/>
</foreach>
```

| Setting | Default | Description |
|---|---|---|
| `chunkSize` | `1048576` (1 MB) | Bytes in each chunk but the last |

### By delimiter

`by-delimiter` cuts the content into chunks of `recordsPerChunk` records, where a record ends with the delimiter.
With a newline delimiter, the default, and 1000 records per chunk, each chunk is 1000 lines, whatever number of bytes
that is:

```xml
<chunking:read-chunked>
    <chunking:chunking-strategy>
        <chunking:by-delimiter recordsPerChunk="1000"/>
    </chunking:chunking-strategy>
</chunking:read-chunked>
<foreach>
    <logger level="INFO" message="#['Chunk $(payload.index) holds $(payload.recordCount) lines']"/>
</foreach>
```

| Setting | Default | Description |
|---|---|---|
| `delimiter` | a newline | The exact sequence that ends a record, as text or as a DataWeave expression |
| `encoding` | `UTF-8` | The character encoding that turns the delimiter into bytes |
| `recordsPerChunk` | required | The number of records in each chunk |
| `maxChunkSize` | `1048576` (1 MB) | The most bytes a chunk may hold |

How records are cut:

- **The delimiter stays in the data.** A chunk ends with the delimiter that ends its last record.
- **Two delimiters in a row are an empty record**, and it counts.
- **Content that ends with a delimiter** does not produce an extra, empty chunk. **Content that ends without one**
  still has its final record, and it counts.
- **The last chunk holds whatever is left**, usually fewer records than `recordsPerChunk`.
- **The delimiter is matched exactly.** Escape sequences are not interpreted: `delimiter="\n"` is a backslash
  followed by `n`. A character that cannot be typed is written as an expression.

A delimiter given as text:

```xml
<chunking:read-chunked>
    <chunking:chunking-strategy>
        <chunking:by-delimiter delimiter="|" recordsPerChunk="100"/>
    </chunking:chunking-strategy>
</chunking:read-chunked>
<foreach>
    <logger level="INFO" message="#['Chunk $(payload.index) holds $(payload.recordCount) records']"/>
</foreach>
```

A delimiter given as an expression, here a carriage return and line feed:

```xml
<chunking:read-chunked>
    <chunking:chunking-strategy>
        <chunking:by-delimiter delimiter="#['\r\n']" encoding="UTF-8" recordsPerChunk="1000"/>
    </chunking:chunking-strategy>
</chunking:read-chunked>
<foreach>
    <logger level="INFO" message="#['Chunk $(payload.index) holds $(payload.recordCount) lines']"/>
</foreach>
```

### The maximum chunk size never cuts a record

`maxChunkSize` bounds the memory a chunk needs, and it never cuts a record. When the next record would take a chunk
past `maxChunkSize` bytes, counting the delimiter that ends the record, the chunk ends before that record, and the
record starts the next chunk whole.

**So a chunk can hold fewer records than `recordsPerChunk` even when more records follow, and this is not an
error.** If `recordsPerChunk` records add up to more than `maxChunkSize`, most chunks will be short. `recordCount`
is how a flow sees it. Here 1000 records of 100 bytes do not fit in 64 KB, so each chunk but the last holds 655:

```xml
<chunking:read-chunked>
    <chunking:chunking-strategy>
        <chunking:by-delimiter recordsPerChunk="1000" maxChunkSize="65536"/>
    </chunking:chunking-strategy>
</chunking:read-chunked>
<foreach>
    <choice>
        <when expression="#[not payload.last and payload.recordCount &lt; 1000]">
            <logger level="WARN" message="#['Chunk $(payload.index) holds only $(payload.recordCount) records']"/>
        </when>
    </choice>
</foreach>
```

A single record larger than `maxChunkSize` cannot fit in any chunk. When the reading reaches it, the read fails, after
the chunks before it have been delivered (see [Errors](#errors)). Content with no delimiter in it is one record, so
it fails the same way once it is larger than `maxChunkSize`.

## Streaming strategies

| Strategy | The chunks can be read | Chunks held in memory |
|---|---|---|
| `non-repeatable` (default) | once, in order | one between reads; up to about three while one is read |
| `sliding-window` | by several readers at the same time | at most `maxInMemoryChunks` |
| `in-memory` | any number of times | every chunk, at most `maxInMemoryChunks` |
| `file-store` | any number of times | at most `maxInMemoryChunks`; the rest on disk |

A chunk is at most `chunkSize` bytes when chunking by size, and at most `maxChunkSize` bytes when chunking by
delimiter, so memory follows from the number of chunks held. `maxInMemoryChunks` must be a positive number on every
strategy that has it.

**When the chunks are released.** What `sliding-window`, `in-memory` and `file-store` hold is released when the
event that read the chunks ends: after the flow's last processor, after any asynchronous work the event started,
and whether the flow succeeded or failed. `file-store` deletes its temporary file then. A read after that fails with
a message saying the chunks were released.

### non-repeatable

The default. The chunks can be read once, in order, as `foreach` does; a second pass over them finds none.

```xml
<chunking:read-chunked>
    <chunking:streaming-strategy>
        <chunking:non-repeatable/>
    </chunking:streaming-strategy>
</chunking:read-chunked>
<foreach>
    <logger level="INFO" message="#['Chunk $(payload.index)']"/>
</foreach>
```

Memory does not grow with the size of the content. Between reads the connector holds one chunk; while it reads a
chunk it needs up to about three times the chunk size (the maximum chunk size when chunking by delimiter).

### sliding-window

Several readers read the chunks, and each chunk is let go once the readers no longer need it, with never more than
`maxInMemoryChunks` held in memory. Nothing is written to disk. Here both routes of a `scatter-gather` read every
chunk, and a chunk is let go as soon as both have read it:

```xml
<chunking:read-chunked>
    <chunking:streaming-strategy>
        <chunking:sliding-window maxInMemoryChunks="10" readers="2"/>
    </chunking:streaming-strategy>
</chunking:read-chunked>
<scatter-gather>
    <route>
        <foreach>
            <logger level="INFO" message="#['Route 1, chunk $(payload.index)']"/>
        </foreach>
    </route>
    <route>
        <foreach>
            <logger level="INFO" message="#['Route 2, chunk $(payload.index)']"/>
        </foreach>
    </route>
</scatter-gather>
```

| Setting | Default | Description |
|---|---|---|
| `maxInMemoryChunks` | `3` | The most chunks held in memory, a positive number; a read that would hold more fails |
| `readers` | not set | How many readers read the chunks; see below |

### Readers under the sliding window

How the sliding window decides when a chunk can go is the thing to understand before using it.

**What counts as a reader.** A reader is anything that reads at least one of the chunks: a `foreach`, a route of a
`scatter-gather` that reads them, a DataWeave expression such as `sizeOf(payload)` or `payload[0]`. Each time the
chunks are read from the start counts as one reader: two `foreach` over the same chunks are two readers. Something
that opens the chunks and reads none of them is not a reader and holds nothing back.

**With `readers` set**, say to 2, a chunk is let go as soon as 2 readers have read it, and never before. Readers
that run at the same time keep memory to the chunks between the slowest and the fastest. A reader that starts late
still finds every chunk, because none is let go until it has read it; but the others can get no more than
`maxInMemoryChunks` ahead of it, and one that would fails with the first message below.

**With `readers` not set**, the window does not know how many readers to wait for. It keeps chunks until
`maxInMemoryChunks` are held, and only then lets go of the oldest that every reader seen so far has read, to make
room for the next. A reader that starts within that many chunks of the first finds every chunk; one that starts
later cannot. Memory then sits at the limit rather than at the minimum.

**When the read fails.** There are three failures. Each fails the reader that meets it, with a message that starts
as shown and says what to change:

| Message starts | What happened | What to change |
|---|---|---|
| `sliding-window: reading chunk N would hold more than maxInMemoryChunks (M) chunks in memory` | The chunks held are still owed to a reader that has not read them, and reading one more would pass the limit. With `readers` set, a declared reader has fallen that far behind or has not started; with it not set, a reader that has started has fallen that far behind. | Raise `maxInMemoryChunks`, or use `file-store`. |
| `sliding-window: chunk N is no longer held. readers is not set` | A reader started after the chunks it needed were let go. | Set `readers` to the number of readers. |
| `sliding-window: chunk N was already let go, because readers is R` | More readers read the chunks than `readers` says, and this one needed a chunk the declared readers had all read. | Set `readers` to the number of readers, counting each pass. |

The failure reaches the flow as described under [Errors](#errors).

**Readers in a `scatter-gather`.** The routes of a `scatter-gather` inside a flow run at the same time, each on its
own thread, so two `foreach` routes read the chunks together. They do not move in step: each reads at its own pace,
and a route that does little work per chunk can get several chunks ahead of another. `maxInMemoryChunks` has to
cover that gap, or the faster route fails with the first message above. Routes that do similar, substantial work for
each chunk stay within a few chunks of each other; for routes that do little, allow more room than the default of 3.

The routes run one after another instead, and the second reader starts only when the first has finished, when:

- the `scatter-gather` has `maxConcurrency="1"`;
- it runs inside a transaction;
- it is not inside a flow, as when it is written directly in an MUnit test.

Then the content must fit within `maxInMemoryChunks`, as for any readers one after another.

**Readers one after another**, such as a `foreach` after a `foreach`, work while the content is no more than
`maxInMemoryChunks` chunks: the first reader reads them all before the second starts, so all of them must be held.
Beyond that the read fails cleanly; it never holds more.

**When to use `in-memory` or `file-store` instead.** Use one of them when the chunks are read one after another over
content larger than `maxInMemoryChunks`, when a reader may start long after the others, when the readers' pace can
differ widely, or when the number of readers is not known. `in-memory` holds every chunk; `file-store` holds a few
and writes the rest to disk.

### in-memory

The chunks can be read any number of times, by any number of readers, because every chunk read is kept in memory,
up to `maxInMemoryChunks`. When the next chunk would be one more than that, the read fails with a message giving the
limit; no chunk is dropped. With the default of 10 and 1 MB chunks, up to about 10 MB is held.

```xml
<chunking:read-chunked>
    <chunking:streaming-strategy>
        <chunking:in-memory maxInMemoryChunks="100"/>
    </chunking:streaming-strategy>
</chunking:read-chunked>
<foreach>
    <logger level="INFO" message="#['First pass, chunk $(payload.index)']"/>
</foreach>
<foreach>
    <logger level="INFO" message="#['Second pass, chunk $(payload.index)']"/>
</foreach>
```

| Setting | Default | Description |
|---|---|---|
| `maxInMemoryChunks` | `10` | The most chunks held in memory, a positive number; reading one more fails the read |

### file-store

The chunks can be read any number of times, by any number of readers, with at most `maxInMemoryChunks` of them in
memory. The most recently read chunks stay in memory; older chunks are written to a temporary file and read back
when a reader needs them. Here a second loop reads every chunk again, most of them from the file:

```xml
<chunking:read-chunked>
    <chunking:streaming-strategy>
        <chunking:file-store maxInMemoryChunks="2"/>
    </chunking:streaming-strategy>
</chunking:read-chunked>
<foreach>
    <logger level="INFO" message="#['First pass, chunk $(payload.index)']"/>
</foreach>
<foreach>
    <logger level="INFO" message="#['Second pass, chunk $(payload.index)']"/>
</foreach>
```

| Setting | Default | Description |
|---|---|---|
| `maxInMemoryChunks` | `10` | The most chunks held in memory, a positive number; older chunks go to the temporary file |

- The file is created in the directory named by the `java.io.tmpdir` system property, named
  `mule-chunking-file-store-*.chunks`, only when a chunk has to be written. It needs as much disk space as the
  content less what is held in memory.
- The file is deleted when the event that read the chunks ends, as described under
  [When the chunks are released](#streaming-strategies).
- The connector writes the file itself, so this strategy does not need the Mule Enterprise runtime.

### The strategies beside Mule's own

The connector's strategies and settings have the connector's own names. Where one corresponds to a Mule streaming
setting, it works like it, with these differences:

| Connector | Mule | How it differs |
|---|---|---|
| `non-repeatable` | `non-repeatable-iterable` | The same idea: read once, in order. |
| `in-memory` `maxInMemoryChunks` | `maxBufferSize` on `repeatable-in-memory-iterable` | Counted in chunks, in the connector's own buffer, not Mule's. Default 10; MuleSoft's documentation states no default for `maxBufferSize`. The buffer grows as chunks arrive, so there is nothing like `initialBufferSize` or `bufferSizeIncrement` to set. |
| `file-store` `maxInMemoryChunks` | `inMemoryObjects` on `repeatable-file-store-iterable` | Counted in chunks, in the connector's own store, not Mule's. The most recent chunks stay in memory and each older one is written to disk as the next arrives. Default 10, where Mule's is 500. No Enterprise runtime needed. |
| `sliding-window` `maxInMemoryChunks`, `readers` | none | No Mule counterpart. |

## Errors

### Errors the operation raises

These are raised by `read-chunked` itself, before any chunk is read, so a flow can handle them by type:

| Error | When |
|---|---|
| `CHUNKING:INVALID_PARAMETER` | A value is not valid. Parent of the three errors below, so handling it catches all of them. Raised itself for an `encoding` the runtime does not recognise and for a streaming strategy setting that is not positive (`maxInMemoryChunks`, `readers`). |
| `CHUNKING:INVALID_CHUNK_SIZE` | `chunkSize` or `maxChunkSize` is not a positive number of bytes |
| `CHUNKING:INVALID_RECORDS_PER_CHUNK` | `recordsPerChunk` is not positive |
| `CHUNKING:INVALID_DELIMITER` | The delimiter is empty. An empty delimiter written as text is rejected when the application loads, so in practice this comes from an expression that gives empty text. |
| `CHUNKING:READ_ERROR` | There is no content to read: the content is `null` |

Each message says what was wrong and ends with the value received, for example
`recordsPerChunk must be a positive number of records (got: 0)`.

```xml
<try>
    <chunking:read-chunked>
        <chunking:chunking-strategy>
            <chunking:by-delimiter recordsPerChunk="0"/>
        </chunking:chunking-strategy>
    </chunking:read-chunked>
    <error-handler>
        <on-error-continue type="CHUNKING:INVALID_PARAMETER">
            <logger level="ERROR" message="#[error.errorType.identifier ++ ': ' ++ error.description]"/>
        </on-error-continue>
    </error-handler>
</try>
```

### Failures while the chunks are read

The content is read while the flow reads the chunks, after `read-chunked` has returned. A failure then is raised to
the flow at once, after the chunks before it, and the content is never reported as having ended early. Mule reports
these failures with its own error types, not the connector's:

- inside `foreach`, `MULE:UNKNOWN`. An error handler cannot name this type (an application that does fails to
  build), so handle these failures with `ANY`, as in the example below, and use `error.description` to tell them
  apart;
- inside a DataWeave expression that reads the chunks, `MULE:EXPRESSION`.

The error's `description` ends with the connector's message, which says what happened. The failures are:

- the content cannot be read, for example the source stream fails part-way;
- a record is larger than `maxChunkSize` (by delimiter);
- one of the three `sliding-window` failures under [Readers under the sliding window](#readers-under-the-sliding-window);
- `in-memory` reaches its limit;
- `file-store` cannot write or read its temporary file;
- the chunks are read after they were released.

```xml
<chunking:read-chunked>
    <chunking:chunking-strategy>
        <chunking:by-delimiter recordsPerChunk="1000" maxChunkSize="65536"/>
    </chunking:chunking-strategy>
</chunking:read-chunked>
<try>
    <foreach>
        <logger level="INFO" message="#['Chunk $(payload.index) holds $(payload.recordCount) records']"/>
    </foreach>
    <error-handler>
        <on-error-continue type="ANY">
            <logger level="ERROR" message="#['Reading the chunks failed: ' ++ error.description]"/>
        </on-error-continue>
    </error-handler>
</try>
```

## Logging

The connector logs nothing at INFO: what is worth an INFO line is for the application's flow to decide.

- **DEBUG**: one line for each invocation of `read-chunked`, with its chunking and streaming strategies and their
  settings; a line for each chunk read; when `file-store` starts writing to its temporary file, and when it deletes
  it; and what `sliding-window` held when it lets go of the chunks.
- **WARN**: the content could not be closed after reading, or `file-store` could not delete its temporary file.
- Failures a flow must handle are raised as errors, not logged.

To see the DEBUG lines, set the logger `com.mulesoftforge.mule.extension.chunking` to DEBUG in the application's
`log4j2.xml`.

## Upgrading from 0.2.0

1.0.0 changes the operation's settings, and 1.0.0-beta1 already has those changes. See [CHANGELOG.md](CHANGELOG.md)
for every change a 0.2.0 application must make.

## License

Apache License 2.0. See the [LICENSE](LICENSE) file.

## Contributing

Contributions are welcome. See the
[MuleSoft Forge contributing guide](https://github.com/MuleSoft-Forge/.github/blob/main/CONTRIBUTING.md).

## Links

- [GitHub repository](https://github.com/MuleSoft-Forge/mule-chunking-connector)
- [MuleSoft Forge documentation](https://docs.mulesoftforge.com/connectors/mule-chunking-connector/)
- [Report an issue](https://github.com/MuleSoft-Forge/mule-chunking-connector/issues)
