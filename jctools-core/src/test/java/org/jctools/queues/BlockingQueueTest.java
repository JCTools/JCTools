package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;

public class BlockingQueueTest {

    @Test
    public void basicSingleThreaded() throws Exception
    {
        ConcurrentQueueSpec qs = ConcurrentQueueSpec.createBoundedSpsc(16);
        BlockingQueue<Integer> q = QueueFactory.newBlockingQueue(qs);

        q.offer(1);
        Assert.assertTrue(q.take() == 1);

        q.offer(2);
        q.offer(3);
        q.offer(4);
        Assert.assertTrue(q.take() == 2);
        Assert.assertTrue(q.take() == 3);
        Assert.assertTrue(q.take() == 4);
    }
}
