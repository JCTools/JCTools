package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.util.TestUtil.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.is;
import static org.jctools.util.TestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeThat;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscArray extends QueueSanityTest
{
    public QueueSanityTestMpscArray(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeMpq(0, 1, 1, Ordering.FIFO));
        list.add(makeMpq(0, 1, 1, Ordering.FIFO));
        list.add(makeMpq(0, 1, SIZE, Ordering.FIFO));
        list.add(makeAtomic(0, 1, 1, Ordering.FIFO));
        list.add(makeAtomic(0, 1, 2, Ordering.FIFO));
        list.add(makeAtomic(0, 1, SIZE, Ordering.FIFO));
        list.add(makeUnpadded(0, 1, 1, Ordering.FIFO));
        list.add(makeUnpadded(0, 1, 2, Ordering.FIFO));
        list.add(makeUnpadded(0, 1, SIZE, Ordering.FIFO));

        return list;
    }

    @Test
    public void testOfferPollSemantics() throws Exception
    {
        assumeThat(spec.capacity, is(2));
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicBoolean consumerLock = new AtomicBoolean(true);
        final Queue<Integer> q = this.queue;
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
