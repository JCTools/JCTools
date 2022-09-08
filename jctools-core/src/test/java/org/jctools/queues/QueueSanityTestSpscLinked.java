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
public class QueueSanityTestSpscLinked extends QueueSanityTest
{
    public QueueSanityTestSpscLinked(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(1, 1, 0, Ordering.FIFO));
        list.add(makeAtomic(1, 1, 0, Ordering.FIFO));
        list.add(makeUnpadded(1, 1, 0, Ordering.FIFO));
        return list;
    }
}
