package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscChunked extends MpqSanityTest
{
    public MpqSanityTestMpscChunked(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 4, Ordering.FIFO, new MpscChunkedArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeMpq(0, 1, SIZE, Ordering.FIFO, new MpscChunkedArrayQueue<>(8, SIZE)));// MPSC size SIZE
        return list;
    }

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
