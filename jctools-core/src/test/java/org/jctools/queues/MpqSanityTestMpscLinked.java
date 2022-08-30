package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.TestUtil.*;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscLinked extends MpqSanityTest
{
    public MpqSanityTestMpscLinked(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 0, Ordering.FIFO));// unbounded MPSC
        list.add(makeAtomic(0, 1, 0, Ordering.FIFO));// unbounded MPSC
        list.add(makeUnpadded(0, 1, 0, Ordering.FIFO));// unbounded MPSC
        return list;
    }
}
