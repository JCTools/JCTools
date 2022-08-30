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

import static org.jctools.util.TestUtil.*;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class QueueSanityTestMpmcArray extends QueueSanityTest
{
    public QueueSanityTestMpmcArray(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        // Mpmc minimal size is 2
        list.add(makeMpq(0, 0, 2, Ordering.FIFO));
        list.add(makeMpq(0, 0, SIZE, Ordering.FIFO));
        list.add(makeAtomic(0, 0, 2, Ordering.FIFO));
        list.add(makeAtomic(0, 0, SIZE, Ordering.FIFO));
        list.add(makeUnpadded(0, 0, 2, Ordering.FIFO));
        list.add(makeUnpadded(0, 0, SIZE, Ordering.FIFO));
        return list;
    }

    @Test
    public void testOfferPollSemantics() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        // fill up the queue
        while (q.offer(1))
        {
            ;
        }
        // queue has 2 empty slots
        q.poll();
        q.poll();

        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable()
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
                    if (q.poll() == null)
                    {
                        fail.value++;
                    }
                }
            }
        });
        Thread t2 = new Thread(new Runnable()
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
                    if (q.poll() == null)
                    {
                        fail.value++;
                    }
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(1000);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("Unexpected offer/poll observed", 0, fail.value);
    }
}
