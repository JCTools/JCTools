package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;

public class BasicBlockingQueueTest {

    @Test
    public void basicSingleThreaded() throws Exception
    {
        ConcurrentQueueSpec qs = ConcurrentQueueSpec.createBoundedSpsc(16);
        BlockingQueue<Integer> q = BlockingQueueFactory.newBlockingQueue(qs);

        q.put(1);
        Assert.assertTrue(q.take() == 1);

        q.put(2);
        q.put(3);
        q.put(4);
        Assert.assertTrue(q.take() == 2);
        Assert.assertTrue(q.take() == 3);
        Assert.assertTrue(q.take() == 4);
    }

    /*@Test
    public void checkClassLoaderCache() throws Exception
    {
        ConcurrentQueueSpec qs = ConcurrentQueueSpec.createBoundedSpsc(16);
        BlockingQueue<Integer> q1 = QueueFactory.newBlockingQueue(qs);
        BlockingQueue<Integer> q2 = QueueFactory.newBlockingQueue(qs);

        Assert.assertTrue(q1.getClass() == q2.getClass());
    }*/

}
