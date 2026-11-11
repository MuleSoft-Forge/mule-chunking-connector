package com.mulesoftforge.mule.extension.chunking.test;

import com.mulesoftforge.mule.extension.chunking.api.streaming.SlidingWindowStrategy;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports what the connector's streaming strategies hold, for MUnit tests: the chunks held in memory, the most held
 * at once, the reads not yet released, and the sliding window's cursors and readers. Called from DataWeave, for
 * example {@code #[java!com::mulesoftforge::mule::extension::chunking::test::Held::now()]}.
 *
 * <p>The counts are kept inside the connector, whose internal classes the test application cannot name, so they are
 * read through the connector's own class loader.</p>
 */
public final class Held {

    private static final String COUNTS = "com.mulesoftforge.mule.extension.chunking.internal.streaming.HeldChunks";

    private Held() {
    }

    /**
     * @return {@code inMemory}, {@code peak}, {@code unreleased}, {@code collected}, {@code cursorsOpened} and
     *         {@code readers}, now
     */
    public static Map<String, Object> now() throws Exception {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String name : new String[] {"inMemory", "peak", "unreleased", "collected", "cursorsOpened", "readers"}) {
            counts.put(name, call(name));
        }
        return counts;
    }

    /**
     * Starts the counts again from zero, for the reads started from now on.
     *
     * @return the counts after the reset, as {@link #now()}
     */
    public static Map<String, Object> reset() throws Exception {
        call("reset");
        return now();
    }

    /**
     * Waits until every read has been released, or the time runs out, without prompting a collection.
     *
     * @param millis how long to wait
     * @return what is held then, as {@link #now()}
     */
    public static Map<String, Object> whenReleased(int millis) throws Exception {
        long until = System.currentTimeMillis() + millis;
        while (((Number) call("unreleased")).intValue() > 0 && System.currentTimeMillis() < until) {
            Thread.sleep(20);
        }
        return now();
    }

    private static Object call(String method) throws Exception {
        Class<?> counts = Class.forName(COUNTS, true, SlidingWindowStrategy.class.getClassLoader());
        Method m = counts.getMethod(method);
        return m.invoke(null);
    }
}
