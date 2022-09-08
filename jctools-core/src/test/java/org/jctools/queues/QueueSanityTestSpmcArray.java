package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.*;

@RunWith(Parameterized.class)
public class QueueSanityTestSpmcArray extends QueueSanityTest
{
    public QueueSanityTestSpmcArray(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        // need at least size 2 for this test
        list.add(makeMpq(1, 0, 1, Ordering.FIFO));
        list.add(makeMpq(1, 0, SIZE, Ordering.FIFO));
        list.add(makeAtomic(1, 0, 1, Ordering.FIFO));
        list.add(makeAtomic(1, 0, SIZE, Ordering.FIFO));
        list.add(makeUnpadded(1, 0, 1, Ordering.FIFO));
        list.add(makeUnpadded(1, 0, SIZE, Ordering.FIFO));

        return list;
    }
}
