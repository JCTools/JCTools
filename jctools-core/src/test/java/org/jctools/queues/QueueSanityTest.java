package org.jctools.queues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Queue;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class QueueSanityTest {

    private static final int SIZE = 8192 * 2;

    @SuppressWarnings("rawtypes")
    @Parameterized.Parameters
    public static Collection queues() {
        return Arrays.asList(new Object[][] { 
                { new ConcurrentQueueSpec(1, 1, 1, Ordering.FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(1, 1, 0, Ordering.FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(1, 1, SIZE, Ordering.FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(1, 0, 1, Ordering.FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(1, 0, SIZE, Ordering.FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(0, 1, 0, Ordering.FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(0, 1, 1, Ordering.FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(0, 1, SIZE, Ordering.FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(0, 1, 1, Ordering.PRODUCER_FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(0, 1, SIZE, Ordering.PRODUCER_FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(0, 1, 1, Ordering.NONE, Preference.NONE) },
                { new ConcurrentQueueSpec(0, 1, SIZE, Ordering.NONE, Preference.NONE) },
                { new ConcurrentQueueSpec(0, 0, 1, Ordering.FIFO, Preference.NONE) },
                { new ConcurrentQueueSpec(0, 0, SIZE, Ordering.FIFO, Preference.NONE) }, });
    }

    final Queue<Integer> q;
    final ConcurrentQueueSpec spec;

    public QueueSanityTest(ConcurrentQueueSpec spec) {
        q = QueueFactory.newQueue(spec);
        this.spec = spec;
    }

    @Before
    public void clear() {
        q.clear();
    }

    @Test
    public void sanity() {
        for (int i = 0; i < SIZE; i++) {
            assertNull(q.poll());
            assertEquals(0, q.size());
        }
        int i = 0;
        while (i < SIZE && q.offer(i))
            i++;
        int size = i;
        assertEquals(size, q.size());
        if (spec.ordering == Ordering.FIFO) {
            // expect FIFO
            i = 0;
            Integer p;
            Integer e;
            while ((p = q.peek()) != null) {
                e = q.poll();
                assertEquals(p, e);
                assertEquals(size - (i + 1), q.size());
                assertEquals(e.intValue(), i++);
            }
            assertEquals(size, i);
        } else {
            // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
            int sum = (size - 1) * size / 2;
            i = 0;
            Integer e;
            while ((e = q.poll()) != null) {
                assertEquals(--size, q.size());
                sum -= e.intValue();
            }
            assertEquals(0, sum);
        }
    }
    
    @Test
    public void shortSanity() {
        assertEquals(0, q.size());
        assertTrue(q.isEmpty());
        Integer e = 1;
        q.offer(e);
        assertEquals(1, q.size());
        assertTrue(!q.isEmpty());
        Integer oh = q.poll();
        assertEquals(e, oh);
        assertEquals(0, q.size());
        assertTrue(q.isEmpty());
    }
}
