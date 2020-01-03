package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscUnbounded extends MpqSanityTest
{
    public MpqSanityTestMpscUnbounded(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 0, Ordering.FIFO, new MpscUnboundedArrayQueue<>(2)));
        list.add(makeMpq(0, 1, 0, Ordering.FIFO, new MpscUnboundedArrayQueue<>(64)));
        return list;
    }
}
