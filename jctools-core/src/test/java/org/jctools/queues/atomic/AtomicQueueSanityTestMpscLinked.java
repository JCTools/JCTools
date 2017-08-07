package org.jctools.queues.atomic;

import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.QueueSanityTestMpscLinked;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

@RunWith(Parameterized.class)
public class AtomicQueueSanityTestMpscLinked extends QueueSanityTestMpscLinked
{
    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(0, 1, 0, Ordering.FIFO, null));
        return list;
    }

    public AtomicQueueSanityTestMpscLinked(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

}
