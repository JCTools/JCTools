package org.jctools.queues.varhandle;

import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.varhandle.unpadded.MpscUnboundedXaddVarHandleUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscUnboundedXaddVarHandle extends QueueSanityTest
{
    public QueueSanityTestMpscUnboundedXaddVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        // VarHandle
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddVarHandleArrayQueue<>(1, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddVarHandleArrayQueue<>(64, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddVarHandleArrayQueue<>(1, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddVarHandleArrayQueue<>(64, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddVarHandleArrayQueue<>(64, 3)));
        // VarHandle Unpadded
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddVarHandleUnpaddedArrayQueue<>(1, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddVarHandleUnpaddedArrayQueue<>(64, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddVarHandleUnpaddedArrayQueue<>(64, 3)));
        return list;
    }
}
