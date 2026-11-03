package org.jctools.maps;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Reproduces a lost CAS witness for the zero key of {@link NonBlockingHashMapLong}.
 * <p>
 * The zero key is stored in a dedicated field. {@code putIfMatch} does a one-shot CAS on it and, when the CAS
 * fails, returns a fresh read of the field instead of the CAS witness and does not retry. If another thread
 * flips the value back between the failed CAS and the re-read, {@code putIfAbsent(0, v)} reports an insert it
 * did not make and {@code remove(0, v)} reports a removal it did not make. Every other key loops until its CAS
 * resolves, so the same workload on any other key never fails.
 */
public class NonBlockingHashMapLongZeroKeyTest
{
    private static final int THREADS = 8;
    private static final int ITERATIONS = 100_000;
    private static final int RUNS = 5;

    @Test
    public void putIfAbsentAndRemoveResultsMatchMapContentForZeroKey() throws Exception
    {
        final NonBlockingHashMapLong<Integer> map = new NonBlockingHashMapLong<>();

        for (int run = 0; run < RUNS; run++)
        {
            final AtomicInteger wins = new AtomicInteger();
            final AtomicInteger removals = new AtomicInteger();
            final AtomicReference<Throwable> failure = new AtomicReference<>();

            final Integer one = 1;

            Thread[] threads = new Thread[THREADS];
            for (int t = 0; t < threads.length; t++)
            {
                threads[t] = new Thread(() -> {
                    try
                    {
                        for (int i = 0; i < ITERATIONS; i++)
                        {
                            if (map.putIfAbsent(0L, one) == null)
                            {
                                wins.incrementAndGet();

                                continue;
                            }

                            if (map.remove(0L, one))
                            {
                                removals.incrementAndGet();
                            }
                        }
                    }
                    catch (Throwable e)
                    {
                        failure.compareAndSet(null, e);
                    }
                });
                threads[t].start();
            }
            for (Thread thread : threads)
            {
                thread.join();
            }
            assertNull(failure.get());

            // Every successful remove was preceded by a successful putIfAbsent, so the key is present
            // exactly when the number of wins exceeds the number of removals by one.
            assertEquals("run " + run + ": putIfAbsent/remove results disagree with the map content",
                wins.get() - removals.get(), map.size());

            map.clear();
        }
    }
}
