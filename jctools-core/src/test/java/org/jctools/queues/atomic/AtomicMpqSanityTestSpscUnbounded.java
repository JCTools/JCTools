package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTestSpscUnbounded;
import org.jctools.queues.SpscUnboundedArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class AtomicMpqSanityTestSpscUnbounded extends MpqSanityTestSpscUnbounded
{

    public AtomicMpqSanityTestSpscUnbounded(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(1, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(2)));
        list.add(makeAtomic(1, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(64)));
        return list;
    }
}
