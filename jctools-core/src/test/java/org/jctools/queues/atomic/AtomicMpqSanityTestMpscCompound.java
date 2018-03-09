package org.jctools.queues.atomic;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTest;
import org.jctools.queues.MpqSanityTestMpscCompound;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.jctools.util.PortableJvmInfo.CPUs;

@RunWith(Parameterized.class)
public class AtomicMpqSanityTestMpscCompound extends MpqSanityTestMpscCompound
{
    public AtomicMpqSanityTestMpscCompound(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(0, 1, CPUs, Ordering.NONE, null));// MPSC size 1
        list.add(makeAtomic(0, 1, SIZE, Ordering.NONE, null));// MPSC size SIZE
        return list;
    }
}
