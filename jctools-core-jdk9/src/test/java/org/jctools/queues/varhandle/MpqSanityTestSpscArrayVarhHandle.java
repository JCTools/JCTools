package org.jctools.queues.varhandle;

import static org.jctools.queues.varhandle.utils.TestUtils.makeMpq;

import java.util.ArrayList;
import java.util.Collection;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MpqSanityTestSpscArrayVarhHandle extends MpqSanityTest
{
    public MpqSanityTestSpscArrayVarhHandle(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(1, 1, 4, Ordering.FIFO));// SPSC size 4
        list.add(makeMpq(1, 1, SIZE, Ordering.FIFO));// SPSC size SIZE
        return list;
    }
}
