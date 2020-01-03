package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.QueueSanityTestMpscUnboundedArray;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class AtomicQueueSanityTestMpscUnboundedArray extends QueueSanityTestMpscUnboundedArray
{
    public AtomicQueueSanityTestMpscUnboundedArray(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(0, 1, 0, Ordering.FIFO, new MpscUnboundedAtomicArrayQueue<Integer>(2)));// MPSC size 1
        list.add(makeQueue(0, 1, 0, Ordering.FIFO, new MpscUnboundedAtomicArrayQueue<Integer>(64)));// MPSC size SIZE
        return list;
    }
}
