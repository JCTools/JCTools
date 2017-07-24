package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class MpqSanityTestSpscUnbounded extends MpqSanityTest
{

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(2)));
        list.add(makeMpq(0, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(64)));
        return list;
    }

    public MpqSanityTestSpscUnbounded(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }
}
