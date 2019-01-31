package org.jctools.queues;

import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import static org.junit.Assert.*;

public abstract class ScQueueRemoveTest
{
    private static <T> void assertQueueEmpty(Queue<T> queue)
    {
        assertNull(queue.peek());
        assertNull(queue.poll());
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    protected abstract Queue<Integer> newQueue();

    private void removeSimple(int removeValue, int expectedFirst, int expectedSecond) throws InterruptedException
    {
        final Queue<Integer> queue = newQueue();
        Thread t = new Thread()
        {
            @Override
            public void run()
            {
                queue.offer(1);
                queue.offer(2);
                queue.offer(3);
            }
        };

        assertQueueEmpty(queue);

        t.start();

        while (queue.size() < 3)
        {
            Thread.yield();
        }

        assertTrue(queue.remove(removeValue));
        // Try to remove again, just to ensure pointers are updated as expected.
        assertFalse(queue.remove(removeValue));
        assertFalse(queue.isEmpty());
        assertEquals(2, queue.size());

        assertEquals(expectedFirst, queue.poll().intValue());
        assertEquals(expectedSecond, queue.poll().intValue());
        assertQueueEmpty(queue);

        t.join();
    }

    @Test
    public void removeConsumerNode() throws InterruptedException
    {
        removeSimple(1, 2, 3);
    }

    @Test
    public void removeInteriorNode() throws InterruptedException
    {
        removeSimple(2, 1, 3);
    }

    @Test
    public void removeProducerNode() throws InterruptedException
    {
        removeSimple(3, 1, 2);
    }

    @Test
    public void removeFailsWhenExpected() throws InterruptedException
    {
        final Queue<Integer> queue = newQueue();
        Thread t = new Thread()
        {
            @Override
            public void run()
            {
                queue.offer(1);
                queue.offer(2);
                queue.offer(3);
            }
        };

        assertQueueEmpty(queue);

        t.start();

        while (queue.size() < 3)
        {
            Thread.yield();
        }

        // Remove an element which doesn't exist.
        assertFalse(queue.remove(4));
        assertFalse(queue.remove(4));
        assertFalse(queue.isEmpty());
        assertEquals(3, queue.size());

        // Verify that none of the links have been modified.
        assertEquals(1, queue.poll().intValue());
        assertEquals(2, queue.poll().intValue());
        assertEquals(3, queue.poll().intValue());
        assertQueueEmpty(queue);

        t.join();
    }

    @Test(timeout = 1000)
    public void removeStressTest() throws InterruptedException
    {
        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicBoolean failed = new AtomicBoolean(false);
        final Queue<Integer> queue = newQueue();

        Thread p = new Thread()
        {
            @Override
            public void run()
            {
                int i = 0;
                try
                {
                    while (running.get())
                    {
                        if (queue.isEmpty())
                        {
                            queue.offer(i++);
                            queue.offer(i++);
                            queue.offer(i++);
                        }
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    failed.set(true);
                    running.set(false);
                }
            }
        };

        Thread c = new Thread()
        {
            @Override
            public void run()
            {
                int i = 0;
                try
                {
                    while (running.get())
                    {
                        if (!queue.isEmpty())
                        {
                            if (!queue.remove(i))
                            {
                                failed.set(true);
                                running.set(false);
                            }
                            i++;
                        }
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    failed.set(true);
                    running.set(false);
                }
            }
        };
        p.start();
        c.start();
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(250));
        running.set(false);
        p.join();
        c.join();
        assertFalse(failed.get());
    }
}
