package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MpscGrowableMessagePassingQueueSanityTest extends MessagePassingQueueSanityTest {
    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 4, Ordering.FIFO, new MpscGrowableArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeMpq(0, 1, SIZE, Ordering.FIFO, new MpscGrowableArrayQueue<>(8, SIZE)));// MPSC size SIZE
        return list;
    }

    public MpscGrowableMessagePassingQueueSanityTest(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue) {
        super(spec, queue);
    }

}
