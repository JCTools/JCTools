package org.jctools.queues.atomic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.QueueSanityTestSpscChunked;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

@RunWith(Parameterized.class)
public class AtomicQueueSanityTestSpscChunked extends QueueSanityTestSpscChunked
{
    public AtomicQueueSanityTestSpscChunked(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeAtomic(1, 1, 16, Ordering.FIFO, new SpscChunkedAtomicArrayQueue<>(8, 16)));// MPSC size 1
        list.add(makeAtomic(1, 1, SIZE, Ordering.FIFO, new SpscChunkedAtomicArrayQueue<>(8, SIZE)));// MPSC size SIZE
        return list;
    }
}
