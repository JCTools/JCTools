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
public class MpqSanityTestMpscArrayVarHandle extends MpqSanityTest
{
    public MpqSanityTestMpscArrayVarHandle(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeVarHandle(0, 1, 1, Ordering.FIFO));// MPSC size 1
        list.add(makeVarHandle(0, 1, SIZE, Ordering.FIFO));// MPSC size SIZE
        list.add(makeVarHandleUnpadded(0, 1, 1, Ordering.FIFO));// MPSC size 1
        list.add(makeVarHandleUnpadded(0, 1, SIZE, Ordering.FIFO));// MPSC size SIZE
        return list;
    }
}
