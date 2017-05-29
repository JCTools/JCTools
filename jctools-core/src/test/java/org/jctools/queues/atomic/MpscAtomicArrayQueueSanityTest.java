package org.jctools.queues.atomic;

import org.jctools.queues.MpscArrayQueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

@RunWith(Parameterized.class)
public class MpscAtomicArrayQueueSanityTest extends MpscArrayQueueSanityTest {
    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(0, 1, 2, Ordering.FIFO, new MpscAtomicArrayQueue<>(2)));// MPSC size 1
        list.add(makeQueue(0, 1, SIZE, Ordering.FIFO, new MpscAtomicArrayQueue<>(SIZE)));// MPSC size SIZE
        return list;
    }

    public MpscAtomicArrayQueueSanityTest(ConcurrentQueueSpec spec, Queue<Integer> queue) {
        super(spec, queue);
    }

}
