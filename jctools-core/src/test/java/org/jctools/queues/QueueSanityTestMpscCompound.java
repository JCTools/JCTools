package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.util.Pow2;

import static org.jctools.util.PortableJvmInfo.CPUs;

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
        list.add(makeQueue(0, 1, Pow2.roundToPowerOfTwo(CPUs), Ordering.NONE, null));
        list.add(makeQueue(0, 1, SIZE, Ordering.NONE, null));
        return list;
    }
}
