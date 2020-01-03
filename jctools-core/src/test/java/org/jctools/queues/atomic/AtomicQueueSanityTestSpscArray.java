package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.QueueSanityTestSpscArray;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)

public class AtomicQueueSanityTestSpscArray extends QueueSanityTestSpscArray
{
    public AtomicQueueSanityTestSpscArray(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(1, 1, 4, Ordering.FIFO, null));
        list.add(makeAtomic(1, 1, SIZE, Ordering.FIFO, null));
        return list;
    }
}
