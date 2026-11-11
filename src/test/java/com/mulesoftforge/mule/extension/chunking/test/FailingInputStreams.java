package com.mulesoftforge.mule.extension.chunking.test;

import java.io.IOException;
import java.io.InputStream;

/**
 * Test sources that fail part-way through, for MUnit tests that need a read failure.
 *
 * <p>Called from DataWeave in MUnit suites, for example
 * {@code #[java!com::mulesoftforge::mule::extension::chunking::test::FailingInputStreams::failAfter(1000)]}.</p>
 */
public final class FailingInputStreams {

    private FailingInputStreams() {
    }

    /**
     * Returns a stream that serves the given number of {@code x} bytes and then throws
     * {@link IOException} on every further read.
     *
     * @param bytes how many bytes to serve before failing
     * @return the failing stream
     */
    public static InputStream failAfter(int bytes) {
        return new InputStream() {
            private int served = 0;

            @Override
            public int read() throws IOException {
                if (served >= bytes) {
                    throw new IOException("Simulated source failure after " + bytes + " bytes");
                }
                served++;
                return 'x';
            }
        };
    }
}
