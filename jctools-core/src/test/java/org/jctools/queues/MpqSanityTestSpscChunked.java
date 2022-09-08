package org.jctools.queues;

import org.jctools.queues.atomic.SpscChunkedAtomicArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.unpadded.SpscChunkedUnpaddedArrayQueue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestSpscChunked extends MpqSanityTest
{
    public MpqSanityTestSpscChunked(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscChunkedArrayQueue<>(8, 16)));// MPSC size 1
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscChunkedArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscChunkedAtomicArrayQueue<>(8, 16)));// MPSC size 1
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscChunkedAtomicArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscChunkedUnpaddedArrayQueue<>(8, 16)));// MPSC size 1
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscChunkedUnpaddedArrayQueue<>(8, SIZE)));// MPSC size SIZE
        return list;
    }

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
