package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.jctools.util.Pow2;
import org.junit.Before;
import org.junit.Test;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

public abstract class MpqSanityTest
{

    static final int SIZE = 8192 * 2;
    static final int CONCURRENT_TEST_DURATION = 250;

    private final MessagePassingQueue<Integer> queue;
    private final ConcurrentQueueSpec spec;
    int count = 0;
    Integer p;

    public MpqSanityTest(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        this.queue = queue;
        this.spec = spec;
    }

    static Object[] makeMpq(int producers, int consumers, int capacity, Ordering ordering, Queue<Integer> q)
    {
        ConcurrentQueueSpec spec = new ConcurrentQueueSpec(producers, consumers, capacity, ordering,
            Preference.NONE);
        if (q == null)
        {
            q = QueueFactory.newQueue(spec);
        }
        return new Object[] {spec, q};
    }

    @Before
    public void clear()
    {
        queue.clear();
    }

    @Test(expected = NullPointerException.class)
    public void relaxedOfferNullResultsInNPE()
    {
        queue.relaxedOffer(null);
    }

    @Test
    public void sanity()
    {
        for (int i = 0; i < SIZE; i++)
        {
            assertNull(queue.relaxedPoll());
            assertTrue(queue.isEmpty());
            assertTrue(queue.size() == 0);
        }
        int i = 0;
        while (i < SIZE && queue.relaxedOffer(i))
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
            while ((p = queue.relaxedPeek()) != null)
            {
                e = queue.relaxedPoll();
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
            while ((e = queue.relaxedPoll()) != null)
            {
                assertEquals(--size, queue.size());
                sum -= e;
            }
            assertEquals(0, sum);
        }
    }

    @Test
    public void sanityDrainBatch()
    {
        assumeThat(spec.ordering, not(Ordering.NONE));

        assertEquals(0, queue.drain(e ->
        {
        }, SIZE));
        assertTrue(queue.isEmpty());
        assertTrue(queue.size() == 0);
        count = 0;
        int i = queue.fill(() ->
        {
            return count++;
        }, SIZE);
        final int size = i;
        assertEquals(size, queue.size());
        if (spec.ordering == Ordering.FIFO)
        {
            // expect FIFO
            p = queue.relaxedPeek();
            count = 0;
            int drainCount = 0;
            i = 0;
            do
            {
                i += drainCount = queue.drain(e ->
                {
                    // batch consumption can cause size to differ from following expectation
                    // this is because elements are 'claimed' in a batch and their consumption lags
                    if (spec.consumers == 1)
                    {
                        assertEquals(p, e); // peek will return the post claim peek
                        assertEquals(size - (count + 1), queue.size()); // size will return the post claim size
                    }
                    assertEquals(count++, e.intValue());
                    p = queue.relaxedPeek();
                });
            }
            while (drainCount != 0);
            p = null;
            assertEquals(size, i);

            assertTrue(queue.isEmpty());
            assertTrue(queue.size() == 0);
        }
        else
        {
        }
    }

    @Test
    public void testSizeIsTheNumberOfOffers()
    {
        int currentSize = 0;
        while (currentSize < SIZE && queue.relaxedOffer(currentSize))
        {
            currentSize++;
            assertFalse(queue.isEmpty());
            assertTrue(queue.size() == currentSize);
        }
        if (spec.isBounded())
        {
            assertEquals(spec.capacity, currentSize);
        }
        else
        {
            assertEquals(SIZE, currentSize);
        }
    }

    @Test
    public void supplyMessageUntilFull()
    {
        assumeThat(spec.isBounded(), is(Boolean.TRUE));
        assumeThat(spec.ordering, not(Ordering.NONE));
        final Val instances = new Val();
        instances.value = 0;
        final MessagePassingQueue.Supplier<Integer> messageFactory = () -> instances.value++;
        final int capacity = queue.capacity();
        int filled = 0;
        while (filled < capacity)
        {
            filled += queue.fill(messageFactory, capacity - filled);
        }
        assertEquals(instances.value, capacity);
        final int noItems = queue.fill(messageFactory, 1);
        assertEquals(noItems, 0);
        assertEquals(instances.value, capacity);
    }

    @Test
    public void whenFirstInThenFirstOut()
    {
        assumeThat(spec.ordering, is(Ordering.FIFO));

        // Arrange
        int i = 0;
        while (i < SIZE && queue.relaxedOffer(i))
        {
            i++;
        }
        final int size = queue.size();

        // Act
        i = 0;
        Integer prev;
        while ((prev = queue.relaxedPeek()) != null)
        {
            final Integer item = queue.relaxedPoll();

            assertThat(item, is(prev));
            assertEquals((size - (i + 1)), queue.size());
            assertThat(item, is(i));
            i++;
        }

        // Assert
        assertThat(i, is(size));
    }

    @Test
    public void test_FIFO_PRODUCER_Ordering() throws Exception
    {
        assumeThat(spec.ordering, is(not((Ordering.FIFO))));

        // Arrange
        int i = 0;
        while (i < SIZE && queue.relaxedOffer(i))
        {
            i++;
        }
        int size = queue.size();

        // Act
        // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
        int sum = (size - 1) * size / 2;
        Integer e;
        while ((e = queue.relaxedPoll()) != null)
        {
            size--;
            assertEquals(size, queue.size());
            sum -= e;
        }

        // Assert
        assertThat(sum, is(0));
    }

    @Test
    public void whenOfferItemAndPollItemThenSameInstanceReturnedAndQueueIsEmpty()
    {
        assertTrue(queue.isEmpty());
        assertTrue(queue.size() == 0);

        // Act
        final Integer e = new Integer(1876876);
        queue.relaxedOffer(e);
        assertFalse(queue.isEmpty());
        assertEquals(1, queue.size());

        final Integer oh = queue.relaxedPoll();
        assertEquals(e, oh);

        // Assert
        assertTrue(queue.isEmpty());
        assertTrue(queue.size() == 0);
    }

    @Test
    public void testPowerOf2Capacity()
    {
        assumeThat(spec.isBounded(), is(true));
        int n = Pow2.roundToPowerOfTwo(spec.capacity);

        for (int i = 0; i < n; i++)
        {
            assertTrue("Failed to insert:" + i, queue.relaxedOffer(i));
        }
        assertFalse(queue.relaxedOffer(n));
    }

    @Test
    public void testHappensBefore() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable()
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
                        q.relaxedOffer(v);
                    }
                    // slow down the producer, this will make the queue mostly empty encouraging visibility
                    // issues.
                    Thread.yield();
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
                    for (int i = 0; i < 10; i++)
                    {
                        Val v = (Val) q.relaxedPeek();
                        if (v != null && v.value == 0)
                        {
                            fail.value = 1;
                            stop.set(true);
                        }
                        q.relaxedPoll();
                    }
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(CONCURRENT_TEST_DURATION);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test
    public void testHappensBeforePrepetualDrain() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable()
        {
            int counter;

            @Override
            public void run()
            {
                while (!stop.get())
                {
                    for (int i = 1; i <= 10; i++)
                    {
                        Val v = new Val();
                        v.value = i;
                        q.relaxedOffer(v);
                    }
                    // slow down the producer, this will make the queue mostly empty encouraging visibility
                    // issues.
                    Thread.yield();
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
                    q.drain(e ->
                    {
                        Val v = (Val) e;
                        if (v != null && v.value == 0)
                        {
                            fail.value = 1;
                            stop.set(true);
                        }
                    }, e ->
                    {
                        return e;
                    }, () ->
                    {
                        return !stop.get();
                    });
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(CONCURRENT_TEST_DURATION);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test
    public void testHappensBeforePrepetualFill() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable()
        {
            int counter;

            @Override
            public void run()
            {
                counter = 1;
                q.fill(() ->
                {
                    Val v = new Val();
                    v.value = 1 + (counter++ % 10);
                    return v;
                }, e ->
                {
                    return e;
                }, () ->
                {
                    // slow down the producer, this will make the queue mostly empty encouraging visibility
                    // issues.
                    Thread.yield();
                    return !stop.get();
                });
            }
        });
        Thread t2 = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    for (int i = 0; i < 10; i++)
                    {
                        Val v = (Val) q.relaxedPeek();
                        int r;
                        if (v != null && (r = v.value) == 0)
                        {
                            fail.value = 1;
                            stop.set(true);
                        }
                        q.relaxedPoll();
                    }
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(CONCURRENT_TEST_DURATION);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test
    public void testHappensBeforePrepetualFillDrain() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable()
        {
            int counter;

            @Override
            public void run()
            {
                counter = 1;
                q.fill(() ->
                {
                    Val v = new Val();
                    v.value = 1 + (counter++ % 10);
                    return v;
                }, e ->
                {
                    return e;
                }, () ->
                { // slow down the producer, this will make the queue mostly empty encouraging
                    // visibility issues.
                    Thread.yield();
                    return !stop.get();
                });
            }
        });
        Thread t2 = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    q.drain(e ->
                    {
                        Val v = (Val) e;
                        if (v != null && v.value == 0)
                        {
                            fail.value = 1;
                            stop.set(true);
                        }
                    }, e ->
                    {
                        return e;
                    }, () ->
                    {
                        return !stop.get();
                    });
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(CONCURRENT_TEST_DURATION);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test
    public void testSize() throws Exception
    {
        assumeThat(spec.isBounded(), is(true));
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    q.relaxedOffer(1);
                    q.relaxedPoll();
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
                    int size = q.size();
                    if (size != 0 && size != 1)
                    {
                        fail.value++;
                    }
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(CONCURRENT_TEST_DURATION);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("Unexpected size observed", 0, fail.value);

    }

    static final class Val
    {
        public int value;
    }

}
