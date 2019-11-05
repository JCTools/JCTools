package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscBlockingConsumer extends MpqSanityTest
{
    public MpqSanityTestMpscBlockingConsumer(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 1, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(1)));// MPSC size 1
        list.add(makeMpq(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(SIZE)));// MPSC size SIZE
        return list;
    }
}
