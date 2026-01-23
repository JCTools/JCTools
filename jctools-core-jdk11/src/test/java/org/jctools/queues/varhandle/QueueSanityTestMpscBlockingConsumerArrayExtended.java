package org.jctools.queues.varhandle;

import org.jctools.queues.varhandle.MpscBlockingConsumerVarHandleArrayQueue;
import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.queues.varhandle.utils.TestUtils.makeSpec;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscBlockingConsumerArrayExtended extends QueueSanityTest
{
    public QueueSanityTestMpscBlockingConsumerArrayExtended(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        ConcurrentQueueSpec spec = makeSpec(0, 1, SIZE, Ordering.FIFO);
        list.add(new Object[] {spec, new MpscBlockingConsumerVarHandleArrayQueue<Integer>(SIZE)});
        return list;
    }
}
