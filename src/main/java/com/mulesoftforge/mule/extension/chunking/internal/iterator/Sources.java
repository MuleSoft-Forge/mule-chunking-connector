package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * Small helpers for reading a source.
 */
final class Sources {

    private Sources() {
    }

    /**
     * Reads until {@code length} bytes have been read or the source ends.
     *
     * @return the number of bytes read; less than {@code length} only at the end of the source
     */
    static int readFully(InputStream source, byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = source.read(buffer, offset + total, length - total);
            if (read == -1) {
                break;
            }
            total += read;
        }
        return total;
    }

    /**
     * Reports whether the source has ended, by reading one byte and pushing it back.
     */
    static boolean atEnd(PushbackInputStream source) throws IOException {
        int probe = source.read();
        if (probe == -1) {
            return true;
        }
        source.unread(probe);
        return false;
    }
}
