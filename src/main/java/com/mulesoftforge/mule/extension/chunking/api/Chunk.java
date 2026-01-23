package com.mulesoftforge.mule.extension.chunking.api;

/**
 * Represents a single chunk of binary data from a larger stream.
 *
 * <p><b>⚠️ CRITICAL - v0.2.0 BREAKING CHANGE:</b></p>
 * <p>This class is now <b>MUTABLE</b> and the underlying byte array is <b>REUSED</b>
 * between iterations for memory efficiency. You MUST process or copy the data from
 * {@link #getData()} <b>before</b> calling {@code next()} on the iterator, or the
 * contents will be overwritten.</p>
 *
 * <h3>Memory Model (v0.2.0+)</h3>
 * <ul>
 *   <li>Single Chunk instance reused by iterator</li>
 *   <li>Single byte[] buffer reused for all chunks</li>
 *   <li>Memory usage: O(chunkSize), NOT O(fileSize)</li>
 * </ul>
 *
 * <h3>Example (Correct Usage)</h3>
 * <pre>
 * &lt;foreach&gt;
 *     &lt;!-- Process chunk.data immediately --&gt;
 *     &lt;file:write path="output.bin"&gt;
 *         #[payload.data]
 *     &lt;/file:write&gt;
 * &lt;/foreach&gt;
 * </pre>
 *
 * <h3>Example (INCORRECT - Data Loss)</h3>
 * <pre>
 * &lt;!-- DON'T DO THIS: Storing chunks in a list --&gt;
 * &lt;set-variable variableName="chunks" value="#[[]]"/&gt;
 * &lt;foreach&gt;
 *     &lt;!-- ❌ All stored chunks will reference SAME buffer! --&gt;
 *     &lt;set-variable variableName="chunks" value="#[vars.chunks ++ payload]"/&gt;
 * &lt;/foreach&gt;
 * </pre>
 *
 * @since 0.1.0
 */
public class Chunk {

    private byte[] data;
    private int index;
    private long offset;
    private int length;
    private boolean isFirst;
    private boolean isLast;

    /**
     * Default constructor for internal use by ChunkIterator.
     * Creates an empty chunk that will be populated via {@link #update}.
     */
    public Chunk() {
        this.data = null;
        this.index = 0;
        this.offset = 0;
        this.length = 0;
        this.isFirst = false;
        this.isLast = false;
    }

    /**
     * Returns the chunk data.
     *
     * <p><b>⚠️ WARNING:</b> This byte array is <b>REUSED</b> between iterations.
     * You must fully process or copy this data <b>before</b> calling {@code next()}
     * on the iterator, or the contents will be overwritten.</p>
     *
     * <p>The actual data length may be less than {@code getData().length}.
     * Always use {@link #getLength()} to determine how many valid bytes are present.</p>
     *
     * @return The chunk data buffer (shared, mutable)
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Returns the 0-based chunk index in the stream.
     *
     * @return The chunk index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns the byte offset in the original source stream where this chunk starts.
     *
     * @return The byte offset
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Returns the actual number of valid bytes in this chunk.
     *
     * <p>This may be less than {@code getData().length}, especially for the last chunk.
     * Always use this value when processing the data.</p>
     *
     * @return The number of valid bytes
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns true if this is the first chunk (index == 0).
     *
     * @return True if first chunk
     */
    public boolean isFirst() {
        return isFirst;
    }

    /**
     * Returns true if this is the last chunk in the stream.
     *
     * @return True if last chunk
     */
    public boolean isLast() {
        return isLast;
    }

    /**
     * Constructor for v0.1.0 compatibility (ChunkPagingProvider).
     * Creates a defensive copy of the data buffer.
     *
     * @param data The data buffer (will be copied)
     * @param index The chunk index
     * @param offset The byte offset in the source
     * @param length The number of valid bytes
     * @param isFirst True if this is the first chunk
     * @param isLast True if this is the last chunk
     * @deprecated Use no-arg constructor with {@link #update} for v0.2.0 buffer reuse
     */
    @Deprecated
    public Chunk(byte[] data, int index, long offset, int length, boolean isFirst, boolean isLast) {
        this.data = java.util.Arrays.copyOf(data, length);  // Defensive copy for v0.1.0
        this.index = index;
        this.offset = offset;
        this.length = length;
        this.isFirst = isFirst;
        this.isLast = isLast;
    }

    /**
     * Public method to update chunk state for buffer reuse.
     * Used by ChunkIterator to reuse the same Chunk instance.
     *
     * <p><b>⚠️ WARNING:</b> This method updates the chunk to reference the provided
     * data buffer directly without copying. The caller must ensure the buffer is not
     * modified after calling this method until the next update.</p>
     *
     * @param data The data buffer (shared reference, not copied)
     * @param index The chunk index
     * @param offset The byte offset in the source
     * @param length The number of valid bytes in this chunk
     * @param isFirst True if this is the first chunk
     * @param isLast True if this is the last chunk
     */
    public void update(byte[] data, int index, long offset, int length, boolean isFirst, boolean isLast) {
        this.data = data;
        this.index = index;
        this.offset = offset;
        this.length = length;
        this.isFirst = isFirst;
        this.isLast = isLast;
    }

    @Override
    public String toString() {
        return String.format("Chunk[index=%d, offset=%d, length=%d, isFirst=%s, isLast=%s]",
                index, offset, length, isFirst, isLast);
    }
}

