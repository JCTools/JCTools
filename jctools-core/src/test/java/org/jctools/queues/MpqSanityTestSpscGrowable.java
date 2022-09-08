package org.jctools.queues;

import org.jctools.queues.atomic.SpscGrowableAtomicArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.unpadded.SpscGrowableUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestSpscGrowable extends MpqSanityTest
{

    public MpqSanityTestSpscGrowable(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, SIZE)));
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscGrowableAtomicArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscGrowableAtomicArrayQueue<>(8, SIZE)));
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscGrowableUnpaddedArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscGrowableUnpaddedArrayQueue<>(8, SIZE)));
        return list;
    }
}
