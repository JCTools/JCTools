package org.jctools.counters;

import org.jctools.util.JvmInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Tolstopyatov Vsevolod
 */
@RunWith(Parameterized.class)
public class FixedSizeStripedLongCounterTest {

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        int stripesCount = JvmInfo.CPUs * 2;
        ArrayList<Object[]> list = new ArrayList<>();
        list.add(new Counter[]{new FixedSizeStripedLongCounterV6(stripesCount)});
        list.add(new Counter[]{new FixedSizeStripedLongCounterV8(stripesCount)});
        return list;
    }

    private final Counter counter;

    public FixedSizeStripedLongCounterTest(Counter counter) {
        this.counter = counter;
    }

    @Test
    public void testCounterSanity() {
        long expected = 1000L;
        for (int i = 0; i < expected; i++) {
            counter.inc();
        }

        assertSanity(expected);
    }

    @Test
    public void testMultipleThreadsCounterSanity() throws Exception {
        int threadsCount = JvmInfo.CPUs;
        int workUnit = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadsCount);

        for (int i = 0; i < threadsCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < workUnit; j++) {
                        counter.inc();
                    }
                    finishLatch.countDown();
                } catch (Exception e) {
                    fail();
                }
            }).start();
        }

        startLatch.countDown();
        finishLatch.await();
        assertSanity(workUnit * threadsCount);
    }

    private void assertSanity(long expected) {
        assertEquals(expected, counter.get());
        assertEquals(expected, counter.getAndReset());
        assertEquals(0L, counter.get());
    }
}
