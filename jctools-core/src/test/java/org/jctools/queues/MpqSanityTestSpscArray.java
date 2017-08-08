package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class MpqSanityTestSpscArray extends MpqSanityTest
{
    public MpqSanityTestSpscArray(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(1, 1, 4, Ordering.FIFO, null));// SPSC size 4
        list.add(makeMpq(1, 1, SIZE, Ordering.FIFO, null));// SPSC size SIZE
        return list;
    }
}
