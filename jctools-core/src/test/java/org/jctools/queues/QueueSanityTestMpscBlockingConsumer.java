package org.jctools.queues;

import org.jctools.queues.atomic.MpscBlockingConsumerAtomicArrayQueue;
import org.jctools.queues.atomic.unpadded.MpscBlockingConsumerAtomicUnpaddedArrayQueue;
import org.jctools.queues.unpadded.MpscBlockingConsumerUnpaddedArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscBlockingConsumer extends QueueSanityTest
{
    public QueueSanityTestMpscBlockingConsumer(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<>();
        list.add(makeParams(0, 1, 2, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(2)));
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(SIZE)));
        list.add(makeParams(0, 1, 2, Ordering.FIFO, new MpscBlockingConsumerAtomicArrayQueue<>(2)));
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerAtomicArrayQueue<>(SIZE)));
        list.add(makeParams(0, 1, 2, Ordering.FIFO, new MpscBlockingConsumerAtomicUnpaddedArrayQueue<>(2)));
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerAtomicUnpaddedArrayQueue<>(SIZE)));
        list.add(makeParams(0, 1, 2, Ordering.FIFO, new MpscBlockingConsumerUnpaddedArrayQueue<>(2)));
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerUnpaddedArrayQueue<>(SIZE)));
        return list;
    }
}
