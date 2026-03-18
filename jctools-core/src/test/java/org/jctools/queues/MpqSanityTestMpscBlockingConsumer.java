package org.jctools.queues;

import org.jctools.queues.atomic.MpscBlockingConsumerAtomicArrayQueue;
import org.jctools.queues.atomic.unpadded.MpscBlockingConsumerAtomicUnpaddedArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.jctools.util.TestUtil.TEST_TIMEOUT;
import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscBlockingConsumer extends MpqSanityTest
{
    public MpqSanityTestMpscBlockingConsumer(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(0, 1, 1, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(1)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(SIZE)));// MPSC size SIZE
        list.add(makeParams(0, 1, 1, Ordering.FIFO, new MpscBlockingConsumerAtomicArrayQueue<>(1)));
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerAtomicArrayQueue<>(SIZE)));
        list.add(makeParams(0, 1, 1, Ordering.FIFO, new MpscBlockingConsumerAtomicUnpaddedArrayQueue<>(1)));
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerAtomicUnpaddedArrayQueue<>(SIZE)));
        return list;
    }
}
