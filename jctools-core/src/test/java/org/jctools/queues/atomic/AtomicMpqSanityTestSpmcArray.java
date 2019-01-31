package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTestSpmcArray;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class AtomicMpqSanityTestSpmcArray extends MpqSanityTestSpmcArray
{
    public AtomicMpqSanityTestSpmcArray(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(1, 0, 1, Ordering.FIFO, null));// SPMC size 1
        list.add(makeAtomic(1, 0, SIZE, Ordering.FIFO, null));// SPMC size SIZE
        return list;
    }
}
