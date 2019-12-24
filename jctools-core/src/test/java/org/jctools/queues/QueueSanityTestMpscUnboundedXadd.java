package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscUnboundedXadd extends QueueSanityTest
{
    public QueueSanityTestMpscUnboundedXadd(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1)));
        list.add(makeQueue(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64)));
        list.add(makeQueue(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1, 2)));
        list.add(makeQueue(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64, 2)));
        return list;
    }
}
