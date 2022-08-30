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
public class QueueSanityTestMpscLinked extends QueueSanityTest
{
    public QueueSanityTestMpscLinked(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 0, Ordering.FIFO));
        list.add(makeAtomic(0, 1, 0, Ordering.FIFO));
        list.add(makeUnpadded(0, 1, 0, Ordering.FIFO));
        return list;
    }
}
