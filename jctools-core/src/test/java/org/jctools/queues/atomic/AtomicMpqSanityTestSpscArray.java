package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTestSpscArray;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class AtomicMpqSanityTestSpscArray extends MpqSanityTestSpscArray
{
    public AtomicMpqSanityTestSpscArray(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(1, 1, 4, Ordering.FIFO, null));// SPSC size 4
        list.add(makeAtomic(1, 1, SIZE, Ordering.FIFO, null));// SPSC size SIZE
        return list;
    }
}
