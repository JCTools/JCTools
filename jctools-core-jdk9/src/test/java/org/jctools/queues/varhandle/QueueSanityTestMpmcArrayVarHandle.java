package org.jctools.queues.varhandle;

import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.queues.varhandle.utils.TestUtils.*;

@RunWith(Parameterized.class)
public class QueueSanityTestMpmcArrayVarHandle extends QueueSanityTest
{
    public QueueSanityTestMpmcArrayVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeVarHandle(0, 0, 2, Ordering.FIFO));// MPMC size 2
        list.add(makeVarHandle(0, 0, SIZE, Ordering.FIFO));// MPMC size SIZE
        list.add(makeVarHandleUnpadded(0, 0, 2, Ordering.FIFO));// MPMC size 2
        list.add(makeVarHandleUnpadded(0, 0, SIZE, Ordering.FIFO));// MPMC size SIZE
        return list;
    }
}
