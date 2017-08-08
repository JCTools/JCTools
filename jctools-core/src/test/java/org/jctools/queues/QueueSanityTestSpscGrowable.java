package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

@RunWith(Parameterized.class)
public class QueueSanityTestSpscGrowable extends QueueSanityTest
{

    public QueueSanityTestSpscGrowable(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(0, 1, 16, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, 16)));
        list.add(makeQueue(0, 1, SIZE, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, SIZE)));
        return list;
    }

}
