package org.jctools.queues;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MpscCompoundQueueTest
{
    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsZeroParallelism()
    {
        new MpscCompoundQueue<Object>(1024, 0);
    }

    @Test
    public void offerPollRoundTripsWithMinimalParallelism()
    {
        MpscCompoundQueue<Integer> queue = new MpscCompoundQueue<Integer>(1024, 1);
        assertTrue(queue.offer(1));
        assertTrue(queue.offer(2));
        assertEquals(Integer.valueOf(1), queue.poll());
        assertEquals(Integer.valueOf(2), queue.poll());
        assertNull(queue.poll());
    }
}
