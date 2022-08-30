package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.util.Pow2;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.PortableJvmInfo.CPUs;
import static org.jctools.util.TestUtil.makeMpq;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscCompound extends QueueSanityTest
{
    public QueueSanityTestMpscCompound(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, Pow2.roundToPowerOfTwo(CPUs), Ordering.NONE));
        list.add(makeMpq(0, 1, SIZE, Ordering.NONE));
        return list;
    }
}
