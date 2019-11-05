package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscBlockingConsumer extends QueueSanityTest
{
    public QueueSanityTestMpscBlockingConsumer(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<>();
        list.add(makeQueue(0, 1, 2, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(2)));
        list.add(makeQueue(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(SIZE)));
        return list;
    }
}
