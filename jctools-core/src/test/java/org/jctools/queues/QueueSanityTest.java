package org.jctools.queues;

import org.jctools.queues.atomic.AtomicQueueFactory;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.jctools.util.Pow2;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.*;
import static org.jctools.queues.matchers.Matchers.emptyAndZeroSize;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

public abstract class QueueSanityTest
{

    public static final int SIZE = 8192 * 2;
    static final int CONCURRENT_TEST_DURATION = 250;
    static final int TEST_TIMEOUT = 30000;

    protected final Queue<Integer> queue;
    protected final ConcurrentQueueSpec spec;

    public QueueSanityTest(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        this.queue = queue;
        this.spec = spec;
    }

    public static Object[] makeQueue(int producers, int consumers, int capacity, Ordering ordering, Queue<Integer> q)
    {
        ConcurrentQueueSpec spec = new ConcurrentQueueSpec(producers, consumers, capacity, ordering,
            Preference.NONE);
        if (q == null)
        {
            q = QueueFactory.newQueue(spec);
        }
        return new Object[] {spec, q};
    }

    public static Object[] makeAtomic(int producers, int consumers, int capacity, Ordering ordering, Queue<Integer> q)
    {
        ConcurrentQueueSpec spec = new ConcurrentQueueSpec(producers, consumers, capacity, ordering,
            Preference.NONE);
        if (q == null)
        {
            q = AtomicQueueFactory.newQueue(spec);
        }
        return new Object[] {spec, q};
    }

    @After
    public void clear()
    {
        queue.clear();
        assertThat(queue, emptyAndZeroSize());
    }

    @Test
    public void toStringWorks()
    {
        assertNotNull(queue.toString());
    }

    @Test
    public void sanity()
    {
        for (int i = 0; i < SIZE; i++)
        {
            assertNull(queue.poll());
            assertThat(queue, emptyAndZeroSize());
        }
        int i = 0;
        while (i < SIZE && queue.offer(i))
        {
            i++;
        }
        int size = i;
        assertEquals(size, queue.size());
        if (spec.ordering == Ordering.FIFO)
        {
            // expect FIFO
            i = 0;
            Integer p;
            Integer e;
            while ((p = queue.peek()) != null)
            {
                e = queue.poll();
                assertEquals(p, e);
                assertEquals(size - (i + 1), queue.size());
                assertEquals(i++, e.intValue());
            }
            assertEquals(size, i);
        }
        else
        {
            // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
            int sum = (size - 1) * size / 2;
            i = 0;
            Integer e;
            while ((e = queue.poll()) != null)
            {
                assertEquals(--size, queue.size());
                sum -= e;
            }
            assertEquals(0, sum);
        }
        assertNull(queue.poll());
        assertThat(queue, emptyAndZeroSize());
    }

    @Test
    public void testSizeIsTheNumberOfOffers()
    {
        int currentSize = 0;
        while (currentSize < SIZE && queue.offer(currentSize))
        {
            currentSize++;
            assertThat(queue, hasSize(currentSize));
        }
    }

    @Test
    public void whenFirstInThenFirstOut()
    {
        assumeThat(spec.ordering, is(Ordering.FIFO));

        // Arrange
        int i = 0;
        while (i < SIZE && queue.offer(i))
        {
            i++;
        }
        final int size = queue.size();

        // Act
        i = 0;
        Integer prev;
        while ((prev = queue.peek()) != null)
        {
            final Integer item = queue.poll();

            assertThat(item, is(prev));
            assertThat(queue, hasSize(size - (i + 1)));
            assertThat(item, is(i));
            i++;
        }

        // Assert
        assertThat(i, is(size));
    }

    @Test
    public void test_FIFO_PRODUCER_Ordering() throws Exception
    {
        assumeThat(spec.ordering, is((Ordering.FIFO)));

        // Arrange
        int i = 0;
        while (i < SIZE && queue.offer(i))
        {
            i++;
        }
        int size = queue.size();

        // Act
        // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
        int sum = (size - 1) * size / 2;
        Integer e;
        while ((e = queue.poll()) != null)
        {
            size--;
            assertThat(queue, hasSize(size));
            sum -= e;
        }

        // Assert
        assertThat(sum, is(0));
    }

    @Test(expected = NullPointerException.class)
    public void offerNullResultsInNPE()
    {
        queue.offer(null);
    }

    @Test
    public void whenOfferItemAndPollItemThenSameInstanceReturnedAndQueueIsEmpty()
    {
        assertThat(queue, emptyAndZeroSize());

        // Act
        final Integer e = new Integer(1876876);
        queue.offer(e);
        assertFalse(queue.isEmpty());
        assertEquals(1, queue.size());

        final Integer oh = queue.poll();
        assertEquals(e, oh);

        // Assert
        assertThat(oh, sameInstance(e));
        assertThat(queue, emptyAndZeroSize());
    }

    @Test
    public void testPowerOf2Capacity()
    {
        assumeThat(spec.isBounded(), is(true));
        int n = Pow2.roundToPowerOfTwo(spec.capacity);

        for (int i = 0; i < n; i++)
        {
            assertTrue("Failed to insert:" + i, queue.offer(i));
        }
        assertFalse(queue.offer(n));
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBefore() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue q = queue;
        final Val fail = new Val();
        final Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    for (int i = 1; i <= 10; i++)
                    {
                        Val v = new Val();
                        v.value = i;
                        q.offer(v);
                    }
                    // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                    Thread.yield();
                }
            }
        };
        Thread[] producers = producers(runnable);
        Thread consumer = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    for (int i = 0; i < 10; i++)
                    {
                        Val v = (Val) q.peek();
                        if (v != null && v.value == 0)
                        {
                            fail.value = 1;
                            stop.set(true);
                        }
                        q.poll();
                    }
                }
            }
        });

        startWaitJoin(stop, producers, consumer);
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testSize() throws Exception
    {
        final int capacity = spec.capacity == 0 ? Integer.MAX_VALUE : spec.capacity;

        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        final Val fail = new Val();
        final Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    q.offer(1);
                    q.poll();
                }
            }
        };
        final Thread[] producersConsumers;
        if (!spec.isMpmc())
        {
            producersConsumers = new Thread[1];
        }
        else
        {
            producersConsumers = new Thread[Runtime.getRuntime().availableProcessors() - 1];
        }
        for (int i = 0; i < producersConsumers.length; i++)
        {
            producersConsumers[i] = new Thread(runnable);
        }

        Thread observer = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                final int max = Math.min(producersConsumers.length, capacity);
                while (!stop.get())
                {
                    int size = q.size();
                    if (size < 0 && size > max)
                    {
                        fail.value++;
                    }
                }
            }
        });

        startWaitJoin(stop, producersConsumers, observer);
        assertEquals("Unexpected size observed", 0, fail.value);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollAfterIsEmpty() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        final Val fail = new Val();
        final Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    q.offer(1);
                    // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                    Thread.yield();
                }
            }
        };
        Thread[] producers = producers(runnable);
        Thread consumer = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    if (!q.isEmpty() && q.poll() == null)
                    {
                        fail.value++;
                    }
                }
            }
        });

        startWaitJoin(stop, producers, consumer);

        assertEquals("Observed no element in non-empty queue", 0, fail.value);

    }

    private void startWaitJoin(AtomicBoolean stop, Thread[] producers, Thread consumer) throws InterruptedException
    {
        for (Thread t : producers) t.start();
        consumer.start();
        Thread.sleep(CONCURRENT_TEST_DURATION);
        stop.set(true);
        for (Thread t : producers)  t.join();
        consumer.join();
    }

    private Thread[] producers(Runnable runnable)
    {
        Thread[] producers;
        if (spec.producers == 1)
        {
            producers = new Thread[1];
        }
        else
        {
            producers = new Thread[Runtime.getRuntime().availableProcessors() - 1];
        }
        for (int i = 0; i < producers.length; i++)
        {
            producers[i] = new Thread(runnable);
        }
        return producers;
    }

    static final class Val
    {
        public int value;
    }

}
