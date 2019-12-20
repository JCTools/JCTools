package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscGrowable extends QueueSanityTestMpscArray
{
    public QueueSanityTestMpscGrowable(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(0, 1, 4, Ordering.FIFO, new MpscGrowableArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeQueue(0, 1, SIZE, Ordering.FIFO, new MpscGrowableArrayQueue<>(8, SIZE)));// MPSC size SIZE
        return list;
    }
}
