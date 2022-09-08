package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscUnboundedXadd extends MpqSanityTest
{

    public MpqSanityTestMpscUnboundedXadd(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64, 0)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64, 1)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1, 2)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64, 2)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(1, 3)));
        list.add(makeParams(0, 1, 0, Ordering.FIFO, new MpscUnboundedXaddArrayQueue<>(64, 3)));
        return list;
    }
}
