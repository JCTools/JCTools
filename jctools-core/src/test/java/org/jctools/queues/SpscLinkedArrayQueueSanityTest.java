package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SpscLinkedArrayQueueSanityTest extends QueueSanityTest {
    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(1, 1, SIZE, Ordering.FIFO, new SpscChunkedArrayQueue<Integer>(64, SIZE)));
        list.add(makeQueue(1, 1, SIZE, Ordering.FIFO, new SpscGrowableArrayQueue<Integer>(64, SIZE)));
        list.add(makeQueue(1, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<Integer>(16)));
        // minimal sizes
        list.add(makeQueue(1, 1, 16, Ordering.FIFO, new SpscChunkedArrayQueue<Integer>(8, 16)));
        list.add(makeQueue(1, 1, 16, Ordering.FIFO, new SpscGrowableArrayQueue<Integer>(8, 16)));
        return list;
    }

    public SpscLinkedArrayQueueSanityTest(ConcurrentQueueSpec spec, Queue<Integer> queue) {
        super(spec, queue);
    }

}
