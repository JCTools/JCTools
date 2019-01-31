package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpqSanityTestSpscChunked;
import org.jctools.queues.SpscChunkedArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class AtomicMpqSanityTestSpscChunked extends MpqSanityTestSpscChunked
{
    public AtomicMpqSanityTestSpscChunked(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(1, 1, 16, Ordering.FIFO, new SpscChunkedArrayQueue<>(8, 16)));// MPSC size 1
        list.add(makeAtomic(1, 1, SIZE, Ordering.FIFO, new SpscChunkedArrayQueue<>(8, SIZE)));// MPSC size SIZE
        return list;
    }
}
