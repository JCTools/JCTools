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
public class QueueSanityTestSpscLinkedVarHandle extends QueueSanityTest
{
    public QueueSanityTestSpscLinkedVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeVarHandle(1, 1, 0, Ordering.FIFO));// unbounded SPSC
        list.add(makeVarHandleUnpadded(1, 1, 0, Ordering.FIFO));// unbounded SPSC
        return list;
    }
}
