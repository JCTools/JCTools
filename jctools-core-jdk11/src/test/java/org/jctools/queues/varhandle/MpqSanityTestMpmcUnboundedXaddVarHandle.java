package org.jctools.queues.varhandle;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.varhandle.unpadded.MpmcUnboundedXaddVarHandleUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestMpmcUnboundedXaddVarHandle extends MpqSanityTest
{
    public MpqSanityTestMpmcUnboundedXaddVarHandle(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        // VarHandle
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddVarHandleArrayQueue<>(1, 0)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddVarHandleArrayQueue<>(16, 0)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddVarHandleArrayQueue<>(1, 1)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddVarHandleArrayQueue<>(16, 1)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddVarHandleArrayQueue<>(16, 4)));
        // VarHandle Unpadded
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddVarHandleUnpaddedArrayQueue<>(1, 0)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddVarHandleUnpaddedArrayQueue<>(16, 1)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddVarHandleUnpaddedArrayQueue<>(16, 4)));
        return list;
    }
}
