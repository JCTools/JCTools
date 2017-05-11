package org.jctools.queues.atomic;

import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

@RunWith(Parameterized.class)
public class AtomicSpscLinkedArrayQueueSanityTest extends QueueSanityTest {
    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(1, 1, SIZE, Ordering.FIFO, new SpscChunkedAtomicArrayQueue<Integer>(64, SIZE)));
        list.add(makeQueue(1, 1, SIZE, Ordering.FIFO, new SpscGrowableAtomicArrayQueue<Integer>(64, SIZE)));
        list.add(makeQueue(1, 1, 0, Ordering.FIFO, new SpscUnboundedAtomicArrayQueue<Integer>(16)));
        // minimal sizes
        list.add(makeQueue(1, 1, 16, Ordering.FIFO, new SpscChunkedAtomicArrayQueue<Integer>(8, 16)));
        list.add(makeQueue(1, 1, 16, Ordering.FIFO, new SpscGrowableAtomicArrayQueue<Integer>(8, 16)));
        return list;
    }

    public AtomicSpscLinkedArrayQueueSanityTest(ConcurrentQueueSpec spec, Queue<Integer> queue) {
        super(spec, queue);
    }

}
