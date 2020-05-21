package org.jctools.queues;

import org.jctools.queues.QueueSanityTest.Val;
import org.jctools.queues.atomic.AtomicQueueFactory;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.jctools.util.Pow2;
import org.junit.After;
import org.junit.Test;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

public abstract class MpqSanityTest
{

    public static final int SIZE = 8192 * 2;
    static final int CONCURRENT_TEST_DURATION = Integer.getInteger("org.jctools.concTestDurationMs", 500);
    static final int TEST_TIMEOUT = 30000;

    private final MessagePassingQueue<Integer> queue;
    private final ConcurrentQueueSpec spec;
    int count = 0;
    Integer p;
    public static final Integer DUMMY_ELEMENT = 1;

    public MpqSanityTest(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        this.queue = queue;
        this.spec = spec;
    }

    public static Object[] makeMpq(int producers, int consumers, int capacity, Ordering ordering, Queue<Integer> q)
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
    public void clear() throws InterruptedException
    {
        queue.clear();
        assertTrue(queue.isEmpty());
        assertTrue(queue.size() == 0);

    }

    @Test(expected = NullPointerException.class)
    public void relaxedOfferNullResultsInNPE()
    {
        queue.relaxedOffer(null);
    }


    @Test
    public void capacityWorks()
    {
        if (spec.isBounded())
        {
            assertEquals(Pow2.roundToPowerOfTwo(spec.capacity), queue.capacity());
        }
        else
        {
            assertEquals(MessagePassingQueue.UNBOUNDED_CAPACITY, queue.capacity());
        }
    }

    @Test
    public void fillToCapacityOnBounded()
    {
        assumeThat(spec.isBounded(), is(Boolean.TRUE));

        queue.fill(() -> DUMMY_ELEMENT);
        assertEquals(queue.capacity(), queue.size());
    }

    @Test
    public void fillOnUnbounded()
    {
        assumeThat(spec.isBounded(), is(Boolean.FALSE));

        queue.fill(() -> DUMMY_ELEMENT);
        assertTrue(!queue.isEmpty());
    }
    @Test
    public void fillToCapacityInBatches()
    {
        assumeThat(spec.isBounded(), is(Boolean.TRUE));
        Integer element = 1;

        int filled = 0;
        for (int i = 0; i < SIZE; i++)
        {
            filled += queue.fill(() -> DUMMY_ELEMENT, 16);
            assertEquals(filled, queue.size());
            if (filled == queue.capacity())
                break;
        }
        assertEquals(queue.capacity(), queue.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void fillNullSupplier()
    {
        queue.fill(null);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void fillNullSupplierLimit()
    {
        queue.fill(null, 10);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void fillNegativeLimit()
    {
        queue.fill(() -> DUMMY_ELEMENT,-1);
        fail();
    }

    @Test
    public void fill0()
    {
        assertEquals(0, queue.fill(() -> {fail(); return 1;},0));
        assertTrue(queue.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void fillNullSupplierWaiterExit()
    {
        queue.fill(null, i -> i++, () -> true);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void fillSupplierNullWaiterExit()
    {
        queue.fill(() -> DUMMY_ELEMENT, null, () -> true);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void fillSupplierWaiterNullExit()
    {
        queue.fill(() -> DUMMY_ELEMENT, i -> i++, null);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void drainNullConsumer()
    {
        queue.drain(null);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void drainNullConsumerLimit()
    {
        queue.drain(null, 10);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void drainNegativeLimit()
    {
        queue.drain(e -> {},-1);
        fail();
    }

    @Test
    public void drain0()
    {
        queue.offer(DUMMY_ELEMENT);
        assertEquals(0, queue.drain(e -> fail(),0));
        assertEquals(1, queue.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void drainNullConsumerWaiterExit()
    {
        queue.drain(null, i -> i++, () -> true);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void drainSupplierNullWaiterExit()
    {
        queue.drain(e -> fail(), null, () -> true);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void drainSupplierWaiterNullExit()
    {
        queue.drain(e -> fail(), i -> i++, null);
        fail();
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

    int sum;
    @Test
    public void sanityDrainBatch()
    {
        assertEquals(0, queue.drain(e ->
        {
        }, SIZE));
        assertTrue(queue.isEmpty());
        assertTrue(queue.size() == 0);
        count = 0;
        sum = 0;
        int i = queue.fill(() ->
        {
            final int val = count++;
            sum += val;
            return val;
        }, SIZE);
        final int size = i;
        assertEquals(size, queue.size());
        if (spec.ordering == Ordering.FIFO)
        {
            // expect FIFO
            count = 0;
            int drainCount = 0;
            i = 0;
            do
            {
                i += drainCount = queue.drain(e ->
                {
                    assertEquals(count++, e.intValue());
                });
            }
            while (drainCount != 0);
            assertEquals(size, i);

            assertTrue(queue.isEmpty());
            assertTrue(queue.size() == 0);
        }
        else
        {
            int drainCount = 0;
            i = 0;
            do
            {
                i += drainCount = queue.drain(e ->
                {
                    sum -= e.intValue();
                });
            }
            while (drainCount != 0);
            assertEquals(size, i);

            assertTrue(queue.isEmpty());
            assertTrue(queue.size() == 0);
            assertEquals(0, sum);
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
        assumeThat(spec.ordering, is((Ordering.FIFO)));

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

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBefore() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
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
                        q.relaxedOffer(v);
                    }
                    // slow down the producer, this will make the queue mostly empty encouraging visibility
                    // issues.
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
                        Val v1 = (Val) q.relaxedPeek();
                        if (v1 != null && v1.value == 0)
                        {
                            fail.value = 1;
                            stop.set(true);
                        }
                        else
                        {
                            continue;
                        }
                        Val v2 = (Val) q.relaxedPoll();
                        if (v2 == null || v1 != v2)
                        {
                            fail.value = 2;
                            stop.set(true);
                        }
                    }
                }
            }
        });

        stopAll(stop, producers, consumer);
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBeforePerpetualDrain() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
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
                        q.relaxedOffer(v);
                    }
                    // slow down the producer, this will make the queue mostly empty encouraging visibility
                    // issues.
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
                    q.drain(e ->
                    {
                        Val v = (Val) e;
                        if (v != null && v.value == 0)
                        {
                            fail.value = 1;
                            stop.set(true);
                        }
                        if (v == null)
                        {
                            fail.value = 1;
                            stop.set(true);
                            System.out.println("Unexpected: v == null");
                        }
                    }, idle ->
                    {
                        return idle;
                    }, () ->
                    {
                        return !stop.get();
                    });
                }
            }
        });

        stopAll(stop, producers, consumer);
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBeforePerpetualFill() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        final Runnable runnable = new Runnable()
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
                        Val v1 = (Val) q.relaxedPeek();
                        int r;
                        if (v1 != null && (r = v1.value) == 0)
                        {
                            fail.value = 1;
                            stop.set(true);
                        }
                        else
                        {
                            continue;
                        }

                        Val v2 = (Val) q.relaxedPoll();
                        if (v2 == null || v1 != v2)
                        {
                            fail.value = 1;
                            stop.set(true);
                        }
                    }
                }
            }
        });

        stopAll(stop, producers, consumer);
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBeforePerpetualFillDrain() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        final Runnable runnable = new Runnable()
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
        };
        Thread[] producers = producers(runnable);

        Thread consumer = new Thread(new Runnable()
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
                        if (v == null)
                        {
                            fail.value = 1;
                            stop.set(true);
                            System.out.println("Unexpected: v == null");
                        }
                    }, idle ->
                    {
                        return idle;
                    }, () ->
                    {
                        return !stop.get();
                    });
                }
            }
        });

        stopAll(stop, producers, consumer);
        assertEquals("reordering detected", 0, fail.value);
        queue.clear();

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testRelaxedOfferPollObservedSize() throws Exception
    {
        final int capacity = spec.capacity == 0 ? Integer.MAX_VALUE : spec.capacity;
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final Val fail = new Val();
        final Runnable runnable = new Runnable()
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

        stopAll(stop, producersConsumers, observer);
        assertEquals("Unexpected size observed", 0, fail.value);

    }

    private void stopAll(AtomicBoolean stop, Thread[] producers, Thread consumer) throws InterruptedException
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


    @Test(timeout = TEST_TIMEOUT)
    public void testPeekAfterIsEmpty1() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final QueueSanityTest.Val fail = new QueueSanityTest.Val();
        Runnable consumerLoop = new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    if (!q.isEmpty() && q.peek() == null)
                    {
                        fail.value++;
                    }
                    q.poll();
                }
            }
        };
        testIsEmptyInvariant(stop, q, fail, consumerLoop);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPeekAfterIsEmpty2() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final QueueSanityTest.Val fail = new QueueSanityTest.Val();
        Runnable consumerLoop = new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    // can the consumer progress "passed" the producer and confuse `isEmpty`?
                    q.poll();
                    if (!q.isEmpty() && q.peek() == null)
                    {
                        fail.value++;
                    }
                }
            }
        };
        testIsEmptyInvariant(stop, q, fail, consumerLoop);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPeekAfterIsEmpty3() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final QueueSanityTest.Val fail = new QueueSanityTest.Val();
        Runnable consumerLoop = new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    // can the consumer progress "passed" the producer and confuse `size`?
                    q.poll();
                    if (q.size() != 0 && q.peek() == null)
                    {
                        fail.value++;
                    }
                }
            }
        };
        testIsEmptyInvariant(stop, q, fail, consumerLoop);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollAfterIsEmpty1() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final QueueSanityTest.Val fail = new QueueSanityTest.Val();
        Runnable consumerLoop = new Runnable()
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
        };
        testIsEmptyInvariant(stop, q, fail, consumerLoop);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollAfterIsEmpty2() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final QueueSanityTest.Val fail = new QueueSanityTest.Val();
        Runnable consumerLoop = new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    // can the consumer progress "passed" the producer and confuse `isEmpty`?
                    q.poll();
                    if (!q.isEmpty() && q.poll() == null)
                    {
                        fail.value++;
                    }
                }
            }
        };
        testIsEmptyInvariant(stop, q, fail, consumerLoop);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollAfterIsEmpty3() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final QueueSanityTest.Val fail = new QueueSanityTest.Val();
        Runnable consumerLoop = new Runnable()
        {
            @Override
            public void run()
            {
                while (!stop.get())
                {
                    // can the consumer progress "passed" the producer and confuse `size`?
                    q.poll();
                    if (q.size() != 0 && q.poll() == null)
                    {
                        fail.value++;
                    }
                }
            }
        };
        testIsEmptyInvariant(stop, q, fail, consumerLoop);
    }

    private void testIsEmptyInvariant(AtomicBoolean stop, MessagePassingQueue<Integer> q, QueueSanityTest.Val fail, Runnable consumerLoop)
        throws InterruptedException
    {
        testIsEmptyInvariant(stop, fail, consumerLoop, () -> {
            while (!stop.get())
            {
                q.relaxedOffer(1);
                // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                Thread.yield();
            }
        });

        testIsEmptyInvariant(stop, fail, consumerLoop, () -> {
            while (!stop.get())
            {
                q.fill(() -> 1);
                // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                Thread.yield();
            }
        });

        testIsEmptyInvariant(stop, fail, consumerLoop, () -> {
            while (!stop.get())
            {
                q.fill(() -> 1, 1);
                // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                Thread.yield();
            }
        });
        testIsEmptyInvariant(stop, fail, consumerLoop, () -> {
            q.fill(() -> 1, i -> {Thread.yield(); return i;}, () -> !stop.get());
        });

        int capacity = q.capacity();
        if (capacity == MessagePassingQueue.UNBOUNDED_CAPACITY || capacity == 1)
            return;
        int limit = Math.max(capacity/8, 2);
        testIsEmptyInvariant(stop, fail, consumerLoop, () -> {
            while (!stop.get())
            {
                q.fill(() -> 1, 1);
                // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                Thread.yield();
            }
        });

    }

    private void testIsEmptyInvariant(
        AtomicBoolean stop,
        QueueSanityTest.Val fail,
        Runnable consumerLoop,
        Runnable producerLoop)
        throws InterruptedException
    {
        Thread[] producers = producers(producerLoop);

        Thread consumer = new Thread(consumerLoop);

        startWaitJoin(stop, producers, consumer);

        assertEquals("Observed no element in non-empty queue", 0, fail.value);
        stop.set(false);
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
}
