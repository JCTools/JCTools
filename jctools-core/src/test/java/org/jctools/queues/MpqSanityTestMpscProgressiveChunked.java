package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscProgressiveChunked extends MpqSanityTest
{

    public MpqSanityTestMpscProgressiveChunked(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 0, Ordering.FIFO, new MpscProgressiveChunkedQueue<>(1)));
        list.add(makeMpq(0, 1, 0, Ordering.FIFO, new MpscProgressiveChunkedQueue<>(64)));
        return list;
    }
}
