package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class MpqSanityTestMpmcUnboundedXadd extends MpqSanityTest
{
    public MpqSanityTestMpmcUnboundedXadd(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 0)));
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(64, 0)));
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 1)));
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(64, 1)));
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 2)));
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(64, 2)));
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 3)));
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(64, 3)));
        return list;
    }
}
