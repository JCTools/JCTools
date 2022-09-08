package org.jctools.queues;

import org.jctools.queues.atomic.SpscChunkedAtomicArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.unpadded.SpscChunkedUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestSpscChunked extends QueueSanityTest
{
    public QueueSanityTestSpscChunked(ConcurrentQueueSpec spec, Queue<Integer> queue)
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
}
