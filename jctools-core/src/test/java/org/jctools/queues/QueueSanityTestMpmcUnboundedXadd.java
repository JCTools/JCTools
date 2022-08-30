package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestMpmcUnboundedXadd extends QueueSanityTest
{
    public QueueSanityTestMpmcUnboundedXadd(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 0)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 0)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 1)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 1)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 2)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 2)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 3)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 3)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 4)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 4)));
        return list;
    }
}
