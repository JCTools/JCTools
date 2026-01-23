package org.jctools.queues.varhandle;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.queues.varhandle.utils.TestUtils.*;

@RunWith(Parameterized.class)
public class MpqSanityTestSpmcArrayVarHandle extends MpqSanityTest
{
    public MpqSanityTestSpmcArrayVarHandle(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeVarHandle(1, 0, 1, Ordering.FIFO));// SPMC size 1
        list.add(makeVarHandle(1, 0, SIZE, Ordering.FIFO));// SPMC size SIZE
        list.add(makeVarHandleUnpadded(1, 0, 1, Ordering.FIFO));// SPMC size 1
        list.add(makeVarHandleUnpadded(1, 0, SIZE, Ordering.FIFO));// SPMC size SIZE
        return list;
    }
}
