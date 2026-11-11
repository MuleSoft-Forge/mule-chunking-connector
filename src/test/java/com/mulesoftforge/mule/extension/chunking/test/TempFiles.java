package com.mulesoftforge.mule.extension.chunking.test;

import java.io.File;

/**
 * Reports the file-store strategy's temporary files, for MUnit tests. Called from DataWeave, for example
 * {@code #[java!com::mulesoftforge::mule::extension::chunking::test::TempFiles::fileStoreBytes()]}.
 */
public final class TempFiles {

    private static final String PREFIX = "mule-chunking-file-store-";

    private TempFiles() {
    }

    /**
     * @return the total size in bytes of the file-store strategy's temporary files that exist now
     */
    public static long fileStoreBytes() {
        long total = 0;
        for (File file : files()) {
            total += file.length();
        }
        return total;
    }

    /**
     * @return how many of the file-store strategy's temporary files exist now
     */
    public static int fileStoreFiles() {
        return files().length;
    }

    /**
     * Asks the JVM to collect garbage, repeatedly, until no file-store temporary file is left or the time runs out.
     *
     * @param millis how long to keep trying
     * @return how many file-store temporary files are left
     */
    public static int fileStoreFilesAfterCollection(int millis) throws InterruptedException {
        long until = System.currentTimeMillis() + millis;
        int left = fileStoreFiles();
        while (left > 0 && System.currentTimeMillis() < until) {
            System.gc();
            Thread.sleep(100);
            left = fileStoreFiles();
        }
        return left;
    }

    /**
     * @return the names of the file-store strategy's temporary files that exist now
     */
    public static java.util.List<String> names() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (File file : files()) {
            names.add(file.getName());
        }
        return names;
    }

    /**
     * @param name a temporary file's name, as {@link #names()} gives it
     * @return whether that file exists now
     */
    public static boolean exists(String name) {
        return new File(System.getProperty("java.io.tmpdir"), name).exists();
    }

    private static File[] files() {
        File[] files = new File(System.getProperty("java.io.tmpdir")).listFiles((dir, name) -> name.startsWith(PREFIX));
        return files == null ? new File[0] : files;
    }
}
