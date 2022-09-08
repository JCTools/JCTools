package org.jctools.queues.blocking;

import static org.junit.Assert.*;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;

@RunWith(Parameterized.class)
public class BlockingQueueTest
{
    protected final static int         CAPACITY = 32768; // better to have a size power of 2 to test boundaries

    private BlockingQueue<Integer> q;
    private final ConcurrentQueueSpec spec;

    @Parameterized.Parameters
    public static Collection queues() {
        return Arrays.asList(
                test(1, 1, CAPACITY, Ordering.FIFO), test(10, 1, CAPACITY, Ordering.FIFO),
                test(1, 10, CAPACITY, Ordering.FIFO), test(10, 10, CAPACITY, Ordering.FIFO));
    }

    private static Object[] test(int producers, int consumers, int capacity, Ordering ordering) {
        return new Object[] { new ConcurrentQueueSpec(producers, consumers, capacity, ordering,
                Preference.NONE) };
    }

    public BlockingQueueTest(ConcurrentQueueSpec spec)
    {
        this.spec = spec;
    }

    @Before
    public void setUp()
    {
        q = BlockingQueueFactory.newBlockingQueue(spec);
    }

    @Test
    public void testOffer()
    {
        for (int i = 0; i < CAPACITY; i++)
        {
            assertTrue(q.offer(i));
            assertEquals(i + 1, q.size());
        }

        if (spec.isBounded())
        {
            assertFalse(q.offer(0));
        }

        assertEquals(CAPACITY, q.size());
        assertFalse(q.isEmpty());
    }

    @Test
    public void testPoll()
    {
        testOffer();

        assertEquals(CAPACITY, q.size());

        for (int i = 0; i < CAPACITY; i++)
        {
            assertEquals(q.poll(), new Integer(i));
            assertEquals(CAPACITY - (i + 1), q.size());
        }

        assertNull(q.poll());
        assertEquals(q.size(), 0);
        assertTrue(q.isEmpty());
    }

    @Test
    public void testEmptyQueue()
    {
        assertNull(q.poll());
        assertEquals(q.size(), 0);
        assertTrue(q.isEmpty());
    }

    @Test
    public void testClear()
    {
        testOffer();

        q.clear();

        testEmptyQueue();
    }

    @Test
    public void testConcurrentOfferPool() throws Exception
    {
        new Thread()
        {
            @Override
            public void run()
            {
                for (int i = 0; i < CAPACITY;)
                {
                    Integer r = q.poll();
                    if (r != null)
                    {
                        assertEquals(r, new Integer(i));
                        i++;
                    }
                }
            }
        }.start();

        // Wait for the thread to warmup
        Thread.sleep(100);

        // Try to insert to max
        for (int i = 0; i < CAPACITY; i++)
        {
            assertTrue(q.offer(i));
        }

        while (!q.isEmpty())
        {
            Thread.sleep(1);
        }
    }

    @Test
    public void testNonBlockingPutTake() throws Exception
    {
        q.put(42);
        Integer i = q.take();
        assertNotNull(i);
        assertEquals(i, new Integer(42));
    }

    @Test
    public void testBlockingTake() throws Exception
    {
        q.put(0);

        Thread take = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    assertEquals(new Integer(0), q.take());
                    assertEquals(new Integer(1), q.take());
                }
                catch (InterruptedException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };
        take.start();

        // Wait for the thread to read 0
        int timeout = 30; // timeout ~3s
        while (!q.isEmpty() && timeout > 0)
        {
            Thread.sleep(100);
            timeout--;
        }
        assertTrue(timeout > 0);

        assertTrue(take.isAlive());

        q.put(1);

        // take thread should be unlocked
        take.join(3000);

        assertFalse(take.isAlive());
    }

    @Test
    public void testBlockingPut() throws Exception
    {
        if (!spec.isBounded())
        {
            // Unbounded queues don't block on put()
            return;
        }

        // Fill the queue
        testOffer();

        Thread put = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    // this should block
                    q.put(-1);
                }
                catch (InterruptedException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };
        put.start();

        Thread.sleep(100);
        assertTrue(put.isAlive());

        for (int i = 0; i < CAPACITY; i++)
        {
            q.take();
        }

        // put(-1) have unlocked
        assertEquals(new Integer(-1), q.take());

        // put thread should be unlocked
        put.join(3000);

        assertFalse(put.isAlive());
    }

}