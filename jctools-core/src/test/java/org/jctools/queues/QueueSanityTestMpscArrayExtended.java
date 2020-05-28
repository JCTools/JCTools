package org.jctools.queues;

import org.jctools.util.TestUtil.Val;
import org.junit.Assert;
import org.junit.Test;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

public class QueueSanityTestMpscArrayExtended
{
    @Test
    public void testOfferWithThreshold()
    {
        MpscArrayQueue<Integer> queue = new MpscArrayQueue<Integer>(16);
        int i;
        for (i = 0; i < 8; ++i)
        {
            //Offers succeed because current size is below the HWM.
            Assert.assertTrue(queue.offerIfBelowThreshold(i, 8));
        }
        //Not anymore, our offer got rejected.
        Assert.assertFalse(queue.offerIfBelowThreshold(i, 8));
        Assert.assertFalse(queue.offerIfBelowThreshold(i, 7));
        Assert.assertFalse(queue.offerIfBelowThreshold(i, 1));
        Assert.assertFalse(queue.offerIfBelowThreshold(i, 0));

        //Also, the threshold is dynamic and different levels can be set for
        //different task priorities.
        Assert.assertTrue(queue.offerIfBelowThreshold(i, 9));
        Assert.assertTrue(queue.offerIfBelowThreshold(i, 16));
    }

    @Test
    public void testOfferPollSemantics() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicBoolean consumerLock = new AtomicBoolean(true);
        final Queue<Integer> q = new MpscArrayQueue<Integer>(2);
        // fill up the queue
        while (q.offer(1))
        {
            ;
        }
        // queue has 2 empty slots
        q.poll();
        q.poll();

        final Val fail = new Val();
        final Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    if (!q.offer(1))
                    {
                        fail.value++;
                    }

                    while (!consumerLock.compareAndSet(true, false))
                    {
                        ;
                    }
                    if (q.poll() == null)
                    {
                        fail.value++;
                    }
                    consumerLock.lazySet(true);
                }
            }
        };
        Thread t1 = new Thread(runnable);
        Thread t2 = new Thread(runnable);

        t1.start();
        t2.start();
        Thread.sleep(1000);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("Unexpected offer/poll observed", 0, fail.value);
    }
}
