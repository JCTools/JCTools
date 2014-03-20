package io.jaq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConcurrentQueueSanityTest {

    @Parameterized.Parameters
    public static Collection primeNumbers() {
       return Arrays.asList(new Object[][] {
               { new ConcurrentQueueSpec(1, 1, 4096, Growth.BOUNDED, Ordering.FIFO, Preference.NONE)},
               { new ConcurrentQueueSpec(1, 0, 4096, Growth.BOUNDED, Ordering.FIFO, Preference.NONE)},
               { new ConcurrentQueueSpec(0, 1, 4096, Growth.BOUNDED, Ordering.FIFO, Preference.NONE)},
               { new ConcurrentQueueSpec(0, 0, 4096, Growth.BOUNDED, Ordering.FIFO, Preference.NONE)},
       });
    }
    
    final ConcurrentQueue<Object> q;
    public ConcurrentQueueSanityTest(ConcurrentQueueSpec spec) {
        q = ConcurrentQueueFactory.newQueue(spec);
    }

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

}
