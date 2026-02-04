package org.jctools.queues.varhandle;

import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.varhandle.unpadded.SpscGrowableVarHandleUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestSpscGrowableVarHandle extends QueueSanityTest
{
    public QueueSanityTestSpscGrowableVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscGrowableVarHandleArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscGrowableVarHandleArrayQueue<>(8, SIZE)));
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscGrowableVarHandleUnpaddedArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscGrowableVarHandleUnpaddedArrayQueue<>(8, SIZE)));
        return list;
    }
}
