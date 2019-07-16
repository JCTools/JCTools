package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class QueueSanityTestMpmcProgressiveChunked extends QueueSanityTest
{
    public QueueSanityTestMpmcProgressiveChunked(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(0, 0, 0, Ordering.FIFO, new MpmcProgressiveChunkedQueue<>(1)));
        list.add(makeQueue(0, 0, 0, Ordering.FIFO, new MpmcProgressiveChunkedQueue<>(64)));
        return list;
    }

}
