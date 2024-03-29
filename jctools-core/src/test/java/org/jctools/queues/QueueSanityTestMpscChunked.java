package org.jctools.queues;

import org.jctools.queues.atomic.MpscChunkedAtomicArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.unpadded.MpscChunkedUnpaddedArrayQueue;
import org.jctools.queues.atomic.unpadded.MpscChunkedAtomicUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscChunked extends QueueSanityTestMpscArray
{
    public QueueSanityTestMpscChunked(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(0, 1, 4, Ordering.FIFO, new MpscChunkedArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscChunkedArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(0, 1, 4096, Ordering.FIFO, new MpscChunkedArrayQueue<>(32, 4096)));// Netty recycler defaults
        list.add(makeParams(0, 1, 4, Ordering.FIFO, new MpscChunkedAtomicArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscChunkedAtomicArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(0, 1, 4096, Ordering.FIFO, new MpscChunkedAtomicArrayQueue<>(32, 4096)));// Netty recycler defaults
        list.add(makeParams(0, 1, 4, Ordering.FIFO, new MpscChunkedUnpaddedArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscChunkedUnpaddedArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(0, 1, 4096, Ordering.FIFO, new MpscChunkedUnpaddedArrayQueue<>(32, 4096)));// Netty recycler defaults
        list.add(makeParams(0, 1, 4, Ordering.FIFO, new MpscChunkedAtomicUnpaddedArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscChunkedAtomicUnpaddedArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(0, 1, 4096, Ordering.FIFO, new MpscChunkedAtomicUnpaddedArrayQueue<>(32, 4096)));// Netty recycler defaults
        return list;
    }
}
