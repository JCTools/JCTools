package org.jctools.queues;

import org.junit.Test;

public class QueueSanityTestSpscChunkedExtended
{

    @Test
    public void testMaxSizeQueue()
    {
        SpscChunkedArrayQueue queue = new SpscChunkedArrayQueue<Object>(1024, 1000 * 1024 * 1024);
        for (int i = 0; i < 400001; i++)
        {
            queue.offer(i);
        }
    }
}
