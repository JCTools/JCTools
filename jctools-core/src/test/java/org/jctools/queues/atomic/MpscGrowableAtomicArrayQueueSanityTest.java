package org.jctools.queues.atomic;

import org.jctools.queues.MpscArrayQueueSanityTest;
import org.jctools.queues.MpscGrowableArrayQueue;
import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

@RunWith(Parameterized.class)
public class MpscGrowableAtomicArrayQueueSanityTest extends MpscArrayQueueSanityTest {
    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(0, 1, 4, Ordering.FIFO, new MpscGrowableAtomicArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeQueue(0, 1, SIZE, Ordering.FIFO, new MpscGrowableAtomicArrayQueue<>(8, SIZE)));// MPSC size SIZE
        return list;
    }

    public MpscGrowableAtomicArrayQueueSanityTest(ConcurrentQueueSpec spec, Queue<Integer> queue) {
        super(spec, queue);
    }
}
