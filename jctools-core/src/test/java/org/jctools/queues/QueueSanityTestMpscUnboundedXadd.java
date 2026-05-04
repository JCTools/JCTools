package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.atomic.MpscUnboundedXaddAtomicArrayQueue;
import org.jctools.queues.atomic.unpadded.MpscUnboundedXaddAtomicUnpaddedArrayQueue;
import org.jctools.queues.unpadded.MpscUnboundedXaddUnpaddedArrayQueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscUnboundedXadd extends QueueSanityTest
{
    public QueueSanityTestMpscUnboundedXadd(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        // Unsafe
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1, 2)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64, 2)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1, 3)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64, 3)));
        // Atomic
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddAtomicArrayQueue<>(1, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddAtomicArrayQueue<>(64, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddAtomicArrayQueue<>(64, 3)));
        // Unpadded
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddUnpaddedArrayQueue<>(1, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddUnpaddedArrayQueue<>(64, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddUnpaddedArrayQueue<>(64, 3)));
        // Atomic Unpadded
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddAtomicUnpaddedArrayQueue<>(1, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddAtomicUnpaddedArrayQueue<>(64, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddAtomicUnpaddedArrayQueue<>(64, 3)));
        return list;
    }
}
