package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class MpqSanityTestSpscUnbounded extends MpqSanityTest
{

    public MpqSanityTestSpscUnbounded(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(1, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(2)));
        list.add(makeMpq(1, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(64)));
        return list;
    }
}
