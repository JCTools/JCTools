package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

@RunWith(Parameterized.class)
public class QueueSanityTestSpscUnbounded extends QueueSanityTest
{

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(1, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(2)));
        list.add(makeQueue(1, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(64)));
        return list;
    }

    public QueueSanityTestSpscUnbounded(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }
}
