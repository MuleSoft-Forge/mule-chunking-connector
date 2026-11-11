package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts what the repeatable streaming strategies hold, for the reads started since the counts were last
 * {@linkplain #reset() reset}: the chunks held in memory now, the most held at once, the reads whose chunks have not
 * yet been released, the reads freed only when the JVM collected them because Mule never released them, and, for the
 * sliding window, the cursors opened and the readers among them.
 *
 * <p>Nothing in the connector's behaviour depends on these counts. They are kept so that what the strategies hold
 * can be observed from outside, in diagnosis and in the connector's own tests. Each read reports to the counts that
 * were current when it started, so reads started before a reset do not change the counts after it.</p>
 */
public final class HeldChunks {

    private static volatile HeldChunks current = new HeldChunks();

    private final AtomicLong inMemory = new AtomicLong();
    private final AtomicLong peak = new AtomicLong();
    private final AtomicInteger unreleased = new AtomicInteger();
    private final AtomicInteger collected = new AtomicInteger();
    private final AtomicInteger cursors = new AtomicInteger();
    private final AtomicInteger readers = new AtomicInteger();

    private HeldChunks() {
    }

    /** @return the counts a read starting now reports to */
    static HeldChunks current() {
        return current;
    }

    void add(int chunks) {
        long now = inMemory.addAndGet(chunks);
        peak.accumulateAndGet(now, Math::max);
    }

    void remove(int chunks) {
        inMemory.addAndGet(-chunks);
    }

    void open() {
        unreleased.incrementAndGet();
    }

    void release() {
        unreleased.decrementAndGet();
    }

    void collect() {
        collected.incrementAndGet();
    }

    void cursor() {
        cursors.incrementAndGet();
    }

    void reader() {
        readers.incrementAndGet();
    }

    /** @return the chunks held in memory now */
    public static long inMemory() {
        return current.inMemory.get();
    }

    /** @return the most chunks held in memory at once */
    public static long peak() {
        return current.peak.get();
    }

    /** @return the reads whose chunks have not yet been released */
    public static int unreleased() {
        return current.unreleased.get();
    }

    /** @return the reads freed when the JVM collected them, never having been released by Mule */
    public static int collected() {
        return current.collected.get();
    }

    /** @return the sliding-window cursors opened */
    public static int cursorsOpened() {
        return current.cursors.get();
    }

    /** @return the sliding-window cursors that read at least one chunk */
    public static int readers() {
        return current.readers.get();
    }

    /** Starts the counts again from zero, for the reads started from now on. */
    public static void reset() {
        current = new HeldChunks();
    }
}
