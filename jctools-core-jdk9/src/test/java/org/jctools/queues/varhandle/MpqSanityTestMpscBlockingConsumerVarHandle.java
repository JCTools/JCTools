package org.jctools.queues.varhandle;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTest;
import org.jctools.queues.varhandle.MpscBlockingConsumerVarHandleArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.queues.varhandle.utils.TestUtils.makeSpec;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscBlockingConsumerVarHandle extends MpqSanityTest
{
    public MpqSanityTestMpscBlockingConsumerVarHandle(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        ConcurrentQueueSpec spec = makeSpec(0, 1, 1, Ordering.FIFO);
        list.add(new Object[] {spec, new MpscBlockingConsumerVarHandleArrayQueue<Integer>(1)});
        spec = makeSpec(0, 1, SIZE, Ordering.FIFO);
        list.add(new Object[] {spec, new MpscBlockingConsumerVarHandleArrayQueue<Integer>(SIZE)});
        return list;
    }
}
