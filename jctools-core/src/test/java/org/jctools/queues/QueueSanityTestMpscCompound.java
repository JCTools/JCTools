package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.util.Pow2;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jctools.util.PortableJvmInfo.CPUs;
import static org.junit.Assert.assertEquals;

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
