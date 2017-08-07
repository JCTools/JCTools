package org.jctools.queues.atomic;

import org.jctools.queues.QueueSanityTestMpscArray;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

@RunWith(Parameterized.class)
public class AtomicQueueSanityTestMpscArray extends QueueSanityTestMpscArray
{
    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        // need at least size 2 for this test
        list.add(makeQueue(0, 1, 2, Ordering.FIFO, new MpscAtomicArrayQueue<>(2)));
        list.add(makeQueue(0, 1, SIZE, Ordering.FIFO, new MpscAtomicArrayQueue<>(SIZE)));
        return list;
    }

    public AtomicQueueSanityTestMpscArray(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

}
