package org.jctools.queues.varhandle;

import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.varhandle.unpadded.SpscUnboundedVarHandleUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestSpscUnboundedVarHandle extends QueueSanityTest
{
    public QueueSanityTestSpscUnboundedVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedVarHandleArrayQueue<>(2)));
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedVarHandleArrayQueue<>(64)));
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedVarHandleUnpaddedArrayQueue<>(2)));
        list.add(makeParams(1, 1, 0, Ordering.FIFO, new SpscUnboundedVarHandleUnpaddedArrayQueue<>(64)));
        return list;
    }
}
