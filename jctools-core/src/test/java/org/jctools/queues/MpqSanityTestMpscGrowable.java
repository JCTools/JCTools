package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscGrowable extends MpqSanityTest
{
    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 4, Ordering.FIFO, new MpscGrowableArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeMpq(0, 1, SIZE, Ordering.FIFO, new MpscGrowableArrayQueue<>(8, SIZE)));// MPSC size SIZE
        return list;
    }

    public MpqSanityTestMpscGrowable(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

}
