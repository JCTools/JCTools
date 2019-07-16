package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class QueueSanityTestMpmcUnboundedXaddArray extends QueueSanityTest
{
    public QueueSanityTestMpmcUnboundedXaddArray(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1)));
        list.add(makeQueue(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(64)));
        return list;
    }

}
