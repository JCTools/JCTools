package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTestSpscGrowable;
import org.jctools.queues.SpscGrowableArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class AtomicMpqSanityTestSpscGrowable extends MpqSanityTestSpscGrowable
{

    public AtomicMpqSanityTestSpscGrowable(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(1, 1, 16, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, 16)));
        list.add(makeAtomic(1, 1, SIZE, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, SIZE)));
        return list;
    }
}
