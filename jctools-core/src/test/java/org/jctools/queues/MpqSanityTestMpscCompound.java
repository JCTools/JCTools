package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.PortableJvmInfo.CPUs;

@RunWith(Parameterized.class)
@Ignore
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
        list.add(makeMpq(0, 1, CPUs, Ordering.NONE, null));// MPSC size 1
        list.add(makeMpq(0, 1, SIZE, Ordering.NONE, null));// MPSC size SIZE
        return list;
    }
}
