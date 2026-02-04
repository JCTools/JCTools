package org.jctools.queues.varhandle;

import org.jctools.queues.QueueSanityTestMpscArray;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.varhandle.unpadded.MpscUnboundedVarHandleUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscUnboundedVarHandle extends QueueSanityTestMpscArray
{
    public QueueSanityTestMpscUnboundedVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedVarHandleArrayQueue<>(2)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedVarHandleArrayQueue<>(64)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedVarHandleUnpaddedArrayQueue<>(2)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedVarHandleUnpaddedArrayQueue<>(64)));
        return list;
    }
}
