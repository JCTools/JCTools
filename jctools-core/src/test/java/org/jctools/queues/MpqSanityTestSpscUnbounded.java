package org.jctools.queues;

import org.jctools.queues.atomic.SpscUnboundedAtomicArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.unpadded.SpscUnboundedUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestSpscUnbounded extends MpqSanityTest
{

    public MpqSanityTestSpscUnbounded(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(2)));
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(64)));
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedAtomicArrayQueue<>(2)));
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedAtomicArrayQueue<>(64)));
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedUnpaddedArrayQueue<>(2)));
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedUnpaddedArrayQueue<>(64)));
        return list;
    }
}
