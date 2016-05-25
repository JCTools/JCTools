package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MpscChunkedMessagePassingQueueSanityTest extends MessagePassingQueueSanityTest {
    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 4, Ordering.FIFO, new MpscChunkedArrayQueue<>(2,4,true)));// MPSC size 1
        list.add(makeMpq(0, 1, SIZE, Ordering.FIFO, new MpscChunkedArrayQueue<>(8, SIZE, true)));// MPSC size SIZE
        return list;
    }

    public MpscChunkedMessagePassingQueueSanityTest(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue) {
        super(spec, queue);
    }

    @Test
    public void testMaxSizeQueue() {
        MpscChunkedArrayQueue queue = new MpscChunkedArrayQueue<Object>(1024, 1000*1024*1024, true);
        for (int i = 0 ; i < 400001; i++) {
            queue.offer(i);
        }
    }
}
