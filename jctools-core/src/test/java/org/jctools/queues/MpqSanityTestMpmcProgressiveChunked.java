package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class MpqSanityTestMpmcProgressiveChunked extends MpqSanityTest
{
    public MpqSanityTestMpmcProgressiveChunked(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcProgressiveChunkedQueue<>(1)));
        list.add(makeMpq(0, 0, 0, Ordering.FIFO, new MpmcProgressiveChunkedQueue<>(64)));
        return list;
    }


}
