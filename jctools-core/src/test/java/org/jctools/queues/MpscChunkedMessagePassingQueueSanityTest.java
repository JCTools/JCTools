package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)

public class MpscChunkedMessagePassingQueueSanityTest extends MessagePassingQueueSanityTest {
    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 4, Ordering.FIFO, new MpscChunkedArrayQueue<>(4)));// MPSC size 1
        list.add(makeMpq(0, 1, SIZE, Ordering.FIFO, new MpscChunkedArrayQueue<>(2, SIZE)));// MPSC size SIZE
        return list;
    }

    public MpscChunkedMessagePassingQueueSanityTest(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue) {
        super(spec, queue);
    }

}
