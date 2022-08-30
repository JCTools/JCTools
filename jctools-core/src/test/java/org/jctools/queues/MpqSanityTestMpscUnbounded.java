package org.jctools.queues;

import org.jctools.queues.atomic.MpscUnboundedAtomicArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.unpadded.MpscUnboundedUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscUnbounded extends MpqSanityTest
{
    public MpqSanityTestMpscUnbounded(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedArrayQueue<>(2)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedArrayQueue<>(64)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedAtomicArrayQueue<>(2)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedAtomicArrayQueue<>(64)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedUnpaddedArrayQueue<>(2)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedUnpaddedArrayQueue<>(64)));
        return list;
    }
}
