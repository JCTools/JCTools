package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.QueueSanityTestSpmcArray;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)

public class AtomicQueueSanityTestSpmcArray extends QueueSanityTestSpmcArray
{
    public AtomicQueueSanityTestSpmcArray(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(1, 0, 1, Ordering.FIFO, null));
        list.add(makeAtomic(1, 0, SIZE, Ordering.FIFO, null));
        return list;
    }
}
