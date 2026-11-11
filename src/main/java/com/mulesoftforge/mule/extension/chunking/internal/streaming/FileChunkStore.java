package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.chunk.ChunkFactory;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the most recently read chunks in memory, at most {@code maxInMemoryChunks} of them, and writes older chunks
 * to a temporary file, from which they are read back when a cursor asks for them. The file is created when the first
 * chunk is written and deleted on {@link #release()}, which may be called more than once and from any thread.
 */
final class FileChunkStore implements ChunkStore {

    /** Temporary files are named with this prefix, in the directory given by {@code java.io.tmpdir}. */
    static final String FILE_PREFIX = "mule-chunking-file-store-";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileChunkStore.class);

    private final int maxInMemoryChunks;
    private final Deque<Chunk> inMemory = new ArrayDeque<>();
    private int firstInMemory = 0;
    private final List<long[]> onDisk = new ArrayList<>(); // per chunk: file position, length, index, offset
    private final List<Object[]> onDiskFlags = new ArrayList<>(); // per chunk: first, last, recordCount
    private Path file;
    private RandomAccessFile access;

    FileChunkStore(int maxInMemoryChunks) {
        this.maxInMemoryChunks = maxInMemoryChunks;
    }

    @Override
    public synchronized void add(Chunk chunk) {
        inMemory.addLast(chunk);
        while (inMemory.size() > maxInMemoryChunks) {
            write(inMemory.removeFirst());
            firstInMemory++;
        }
    }

    private void write(Chunk chunk) {
        try {
            if (access == null) {
                file = Files.createTempFile(FILE_PREFIX, ".chunks");
                file.toFile().deleteOnExit();
                access = new RandomAccessFile(file.toFile(), "rw");
                LOGGER.debug("file-store: writing chunks beyond the {} held in memory to {}", maxInMemoryChunks, file);
            }
            long position = access.length();
            access.seek(position);
            access.write(chunk.getData());
            onDisk.add(new long[] {position, chunk.getLength(), chunk.getIndex(), chunk.getOffset()});
            onDiskFlags.add(new Object[] {chunk.isFirst(), chunk.isLast(), chunk.getRecordCount()});
        } catch (IOException e) {
            throw new ChunkReadException("file-store: could not write chunk " + chunk.getIndex() + " to "
                + (file == null ? "a temporary file" : file.toString()) + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized Chunk get(int position) {
        if (position >= firstInMemory) {
            int i = 0;
            for (Chunk chunk : inMemory) {
                if (i++ == position - firstInMemory) {
                    return chunk;
                }
            }
        }
        long[] where = onDisk.get(position);
        Object[] flags = onDiskFlags.get(position);
        byte[] data = new byte[(int) where[1]];
        try {
            access.seek(where[0]);
            access.readFully(data);
        } catch (IOException e) {
            throw new ChunkReadException("file-store: could not read chunk " + where[2] + " back from " + file + ": "
                + e.getMessage(), e);
        }
        return ChunkFactory.create(data, (int) where[2], where[3], (Boolean) flags[0], (Boolean) flags[1],
                                   (Integer) flags[2]);
    }

    @Override
    public synchronized int size() {
        return firstInMemory + inMemory.size();
    }

    @Override
    public synchronized int inMemory() {
        return inMemory.size();
    }

    @Override
    public String name() {
        return "file-store";
    }

    @Override
    public synchronized void release() {
        inMemory.clear();
        onDisk.clear();
        onDiskFlags.clear();
        firstInMemory = 0;
        try {
            if (access != null) {
                access.close();
            }
            if (file != null) {
                Files.deleteIfExists(file);
                LOGGER.debug("file-store: deleted {}", file);
            }
        } catch (IOException e) {
            LOGGER.warn("file-store: could not delete temporary file {}: {}", file, e.getMessage());
        } finally {
            access = null;
            file = null;
        }
    }
}
