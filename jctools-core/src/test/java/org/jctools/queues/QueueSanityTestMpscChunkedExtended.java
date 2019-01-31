package org.jctools.queues;

import org.junit.Test;

public class QueueSanityTestMpscChunkedExtended
{
    @Test
    public void testMaxSizeQueue()
    {
        MpscChunkedArrayQueue queue = new MpscChunkedArrayQueue<Object>(1024, 1000 * 1024 * 1024);
        for (int i = 0; i < 400001; i++)
        {
            queue.offer(i);
        }
    }
}
