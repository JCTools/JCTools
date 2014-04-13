package io.jaq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConcurrentQueueSanityTest {

    private static final int SIZE = 8192 * 2;

    @Parameterized.Parameters
    public static Collection queues() {
        return Arrays
                .asList(new Object[][] {
                        { new ConcurrentQueueSpec(1, 1, SIZE, Growth.BOUNDED, Ordering.FIFO, Preference.NONE) },
                        { new ConcurrentQueueSpec(1, 0, SIZE, Growth.BOUNDED, Ordering.FIFO, Preference.NONE) },
                        { new ConcurrentQueueSpec(0, 1, SIZE, Growth.BOUNDED, Ordering.FIFO, Preference.NONE) },
                        { new ConcurrentQueueSpec(0, 1, SIZE, Growth.BOUNDED, Ordering.PRODUCER_FIFO,
                                Preference.NONE) },
                        { new ConcurrentQueueSpec(0, 1, SIZE, Growth.BOUNDED, Ordering.NONE, Preference.NONE) },
                        { new ConcurrentQueueSpec(0, 0, SIZE, Growth.BOUNDED, Ordering.FIFO, Preference.NONE) }, });
    }

    final ConcurrentQueue<Integer> q;
    final ConcurrentQueueSpec spec;

    public ConcurrentQueueSanityTest(ConcurrentQueueSpec spec) {
        q = ConcurrentQueueFactory.newQueue(spec);
        this.spec = spec;
    }

    @Before
    public void clear() {
        q.consumer().clear();
    }

    @Test
    public void testOfferPoll() {
        for (int i = 0; i < SIZE; i++) {
            assertNull(q.consumer().poll());
            assertEquals(0, q.size());
        }
        int i = 0;
        while (q.producer().offer(i++))
            ;
        int size = i - 1;
        assertEquals(size, q.size());
        if (spec.ordering == Ordering.FIFO) {
            // expect FIFO
            i = 0;
            Integer e;
            while ((e = q.consumer().poll()) != null) {
                assertEquals(size - (i + 1), q.size());
                assertEquals(i++, e.intValue());
            }
            assertEquals(size, i);
        } else {
            // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
            int sum = (size - 1) * size / 2;
            i = 0;
            Integer e;
            while ((e = q.consumer().poll()) != null) {
                assertEquals(--size, q.size());
                sum -= e.intValue();
            }
            assertEquals(0, sum);
        }
    }
}
