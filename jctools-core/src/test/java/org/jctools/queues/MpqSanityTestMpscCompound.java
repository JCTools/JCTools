package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.util.Pow2;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.PortableJvmInfo.CPUs;
import static org.jctools.util.TestUtil.makeMpq;

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
        list.add(makeMpq(0, 1, Pow2.roundToPowerOfTwo(CPUs), Ordering.NONE));// MPSC size 1
        list.add(makeMpq(0, 1, SIZE, Ordering.NONE));// MPSC size SIZE
        return list;
    }
}
