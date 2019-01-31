package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.util.Pow2;

import static org.jctools.util.PortableJvmInfo.CPUs;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscCompound extends MpqSanityTest
{
    public MpqSanityTestMpscCompound(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, Pow2.roundToPowerOfTwo(CPUs), Ordering.NONE, null));// MPSC size 1
        list.add(makeMpq(0, 1, SIZE, Ordering.NONE, null));// MPSC size SIZE
        return list;
    }
}
