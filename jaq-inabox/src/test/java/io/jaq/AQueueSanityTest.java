package io.jaq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import io.jaq.spsc.FFBufferWithOfferBatch;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

public class AQueueSanityTest {

    AQueue q = new FFBufferWithOfferBatch<>(4096);

    @Before
    public void clear() {
        q.consumer().clear();
    }

    @Test
    public void testOfferPoll() {
        assertNull(q.consumer().poll());
        Object a = new Object();
        assertTrue(q.producer().offer(a));
        assertEquals(a, q.consumer().poll());
        assertNull(q.consumer().poll());
        assertEquals(0, q.size());
    }

    @Test
    public void testOfferBatch() {
        assertNull(q.consumer().poll());
        assertEquals(0, q.size());
        Object[] ea = new Object[10];
        Object a = new Object();
        Arrays.fill(ea, a);
        assertTrue(q.producer().offer(ea));
        assertEquals(10, q.size());
        for (int i = 9; i >= 0; i--) {
            assertEquals(a, q.consumer().poll());
            assertEquals(i, q.size());
        }
        assertNull(q.consumer().poll());
    }

    @Test
    public void testPollBatch() {
        assertNull(q.consumer().poll());
        assertEquals(0, q.size());
        Object[] ea = new Object[10];
        final Object a = new Object();
        Arrays.fill(ea, a);
        assertTrue(q.producer().offer(ea));
        assertEquals(10, q.size());
        final AtomicInteger counter = new AtomicInteger();
        q.consumer().consumeBatch(new BatchConsumer() {
            @Override
            public void consume(Object e, boolean last) {
                assertEquals(a, e);
                counter.incrementAndGet();
            }
        }, 5);
        assertEquals(5, counter.get());
        q.consumer().consumeBatch(new BatchConsumer() {
            @Override
            public void consume(Object e, boolean last) {
                assertEquals(a, e);
                counter.incrementAndGet();
            }
        }, 10);
        assertNull(q.consumer().poll());
        assertEquals(10, counter.get());
    }

}
