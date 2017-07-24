package org.jctools.queues.atomic;

import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.JvmInfo.CPUs;

@RunWith(Parameterized.class)

public class AtomicQueueSanityTest extends QueueSanityTest
{
    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(1, 1, 1, Ordering.FIFO, null));
        list.add(makeAtomic(1, 1, 0, Ordering.FIFO, null));
        list.add(makeAtomic(1, 1, SIZE, Ordering.FIFO, null));

        list.add(makeAtomic(1, 1, 0, Ordering.FIFO, new SpscUnboundedAtomicArrayQueue<Integer>(16)));
        list.add(makeAtomic(1, 0, 1, Ordering.FIFO, null));
        list.add(makeAtomic(1, 0, SIZE, Ordering.FIFO, null));

        list.add(makeAtomic(0, 1, 0, Ordering.FIFO, null));

        list.add(makeAtomic(0, 1, 1, Ordering.FIFO, null));

        // Compound queue minimal size is the core count
        list.add(makeAtomic(0, 1, CPUs, Ordering.NONE, null));
        list.add(makeAtomic(0, 1, SIZE, Ordering.NONE, null));
        return list;
    }

    public AtomicQueueSanityTest(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

}
