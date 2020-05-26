package org.jctools.util;

public class TestUtil {

    public static void sleepQuietly(long timeMs) {
        try {
            Thread.sleep(timeMs);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }
}
