package com.mulesoftforge.mule.extension.chunking.test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds results that a flow records for an MUnit test to read later, when the flow was not started by the test or
 * its work finished after it replied. Called from DataWeave, for example
 * {@code #[java!com::mulesoftforge::mule::extension::chunking::test::Results::put('key', payload)]}.
 */
public final class Results {

    private static final Map<String, Object> RESULTS = new ConcurrentHashMap<>();

    private Results() {
    }

    /**
     * @param key the result's name
     * @param value the result
     * @return the result
     */
    public static Object put(String key, Object value) {
        RESULTS.put(key, value);
        return value;
    }

    /**
     * Adds a value to the list recorded under the key, for a test that observes each call of a processor.
     *
     * @param key the list's name
     * @param value the value to add
     * @return the value
     */
    @SuppressWarnings("unchecked")
    public static Object append(String key, Object value) {
        ((java.util.List<Object>) RESULTS.computeIfAbsent(key, k -> java.util.Collections.synchronizedList(
            new java.util.ArrayList<>()))).add(value);
        return value;
    }

    /**
     * Waits until a result has been recorded under the key, or the time runs out, and removes it.
     *
     * @param key the result's name
     * @param millis how long to wait
     * @return the result, or {@code null} if none was recorded in time
     */
    public static Object take(String key, int millis) throws InterruptedException {
        long until = System.currentTimeMillis() + millis;
        while (!RESULTS.containsKey(key) && System.currentTimeMillis() < until) {
            Thread.sleep(20);
        }
        return RESULTS.remove(key);
    }
}
