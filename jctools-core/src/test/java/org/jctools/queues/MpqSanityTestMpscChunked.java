package org.jctools.queues;

import org.jctools.queues.atomic.MpscChunkedAtomicArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.unpadded.MpscChunkedUnpaddedArrayQueue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.TestUtil.makeParams;

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
        list.add(makeParams(0, 1, 4, Ordering.FIFO, new MpscChunkedArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscChunkedArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(0, 1, 4, Ordering.FIFO, new MpscChunkedAtomicArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscChunkedAtomicArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(0, 1, 4, Ordering.FIFO, new MpscChunkedUnpaddedArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscChunkedUnpaddedArrayQueue<>(8, SIZE)));// MPSC size SIZE
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
