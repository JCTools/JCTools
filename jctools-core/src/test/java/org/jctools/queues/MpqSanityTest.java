package org.jctools.queues;

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.util.Pow2;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static org.hamcrest.Matchers.is;
import static org.jctools.util.TestUtil.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

public abstract class MpqSanityTest
{

    public static final int SIZE = 8192 * 2;

    protected final MessagePassingQueue<Integer> queue;
    private final ConcurrentQueueSpec spec;
    int count = 0;
    Integer p;
    public static final Integer DUMMY_ELEMENT = 1;

    public MpqSanityTest(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        this.queue = queue;
        this.spec = spec;
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
        List<Thread> threads = new ArrayList<>();
        threads(() -> {
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
        }, spec.producers, threads);

        threads(() -> {
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
        }, 1, threads);

        startWaitJoin(stop, threads);
        assertEquals("reordering detected", 0, fail.value);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBeforePerpetualDrain() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();
        threads(() -> {
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
        }, spec.producers, threads);

        threads(() -> {
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
        }, 1 , threads);

        startWaitJoin(stop, threads);
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBeforePerpetualFill() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();
        threads(() -> {
            Val counter = new Val();
            counter.value = 1;
            q.fill(() ->
            {
                Val v = new Val();
                int c = counter.value++ % 10;
                v.value = 1 + c;
                if (c == 0)
                    Thread.yield();
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
        }, spec.producers, threads);
        threads(() -> {
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
        }, 1, threads);

        startWaitJoin(stop, threads);
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBeforePerpetualFillDrain() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();
        threads(() -> {
            Val counter = new Val();
            counter.value = 1;
            q.fill(() ->
            {
                Val v = new Val();
                v.value = 1 + (counter.value++ % 10);
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
        }, spec.producers, threads);

        threads(() -> {
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
        }, 1, threads);

        startWaitJoin(stop, threads);
        assertEquals("reordering detected", 0, fail.value);
        queue.clear();

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testRelaxedOfferPollObservedSize() throws Exception
    {
        final int capacity = !spec.isBounded() ? Integer.MAX_VALUE : queue.capacity();
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();
        threads(() -> {
            while (!stop.get())
            {
                if(q.relaxedOffer(1))
                    while (q.relaxedPoll() == null);
            }
        }, !spec.isMpmc()? 1: 0, threads);

        int threadCount = threads.size();
        threads(() -> {
            final int max = Math.min(threadCount, capacity);
            while (!stop.get())
            {
                int size = q.size();
                if (size < 0 || size > max)
                {
                    fail.value++;
                }
            }
        }, 1, threads);

        startWaitJoin(stop, threads);
        assertEquals("Unexpected size observed", 0, fail.value);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPeekAfterIsEmpty1() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final Val fail = new Val();

        testIsEmptyInvariant(stop, q, fail, () -> {
            while (!stop.get())
            {
                if (!q.isEmpty() && q.peek() == null)
                {
                    fail.value++;
                }
                q.poll();
            }
        });
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPeekAfterIsEmpty2() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final Val fail = new Val();

        testIsEmptyInvariant(stop, q, fail, () -> {
            while (!stop.get())
            {
                // can the consumer progress "passed" the producer and confuse `isEmpty`?
                q.poll();
                if (!q.isEmpty() && q.peek() == null)
                {
                    fail.value++;
                }
            }
        });
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPeekAfterIsEmpty3() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final Val fail = new Val();

        testIsEmptyInvariant(stop, q, fail, () -> {
            while (!stop.get())
            {
                // can the consumer progress "passed" the producer and confuse `size`?
                q.poll();
                if (q.size() != 0 && q.peek() == null)
                {
                    fail.value++;
                }
            }
        });
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollAfterIsEmpty1() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final Val fail = new Val();

        testIsEmptyInvariant(stop, q, fail, () -> {
            while (!stop.get())
            {
                if (!q.isEmpty() && q.poll() == null)
                {
                    fail.value++;
                }
            }
        });
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollAfterIsEmpty2() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final Val fail = new Val();

        testIsEmptyInvariant(stop, q, fail, () -> {
            while (!stop.get())
            {
                // can the consumer progress "passed" the producer and confuse `isEmpty`?
                q.poll();
                if (!q.isEmpty() && q.poll() == null)
                {
                    fail.value++;
                }
            }
        });
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollAfterIsEmpty3() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;
        final Val fail = new Val();
        boolean slowSize = !spec.isBounded() && !(queue instanceof IndexedQueue);

        testIsEmptyInvariant(stop, q, fail, () -> {
            while (!stop.get())
            {
                // can the consumer progress "passed" the producer and confuse `size`?
                q.poll();
                int size = q.size();
                if (size != 0 && q.poll() == null)
                {
                    fail.value++;
                }
                if (slowSize) {
                    q.clear();
                }
            }
        });
    }

    private void testIsEmptyInvariant(AtomicBoolean stop, MessagePassingQueue<Integer> q, Val fail, Runnable consumerLoop)
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
                int items = q.fill(() -> 1);
                // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                LockSupport.parkNanos(items);
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
                int items = q.fill(() -> 1, limit);
                // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                LockSupport.parkNanos(items);
            }
        });

    }

    private void testIsEmptyInvariant(
        AtomicBoolean stop,
        Val fail,
        Runnable consumerLoop,
        Runnable producerLoop)
        throws InterruptedException
    {
        List<Thread> threads = new ArrayList<>();
        threads(producerLoop, spec.producers, threads);
        threads(consumerLoop, 1, threads);
        startWaitJoin(stop, threads, 4);

        assertEquals("Observed no element in non-empty queue", 0, fail.value);
        clear();
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testSizeLtZero() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;

        // producer check size and offer
        final Val pFail = new Val();
        List<Thread> threads = new ArrayList<>();
        threads(() -> {
            while (!stop.get()) {
                if (q.size() < 0) {
                    pFail.value++;
                }

                q.offer(1);
                Thread.yield();
            }
        }, spec.producers, threads);

        // consumer poll and check size
        final Val cFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                q.poll();

                if (q.size() < 0) {
                    cFail.value++;
                }
            }
        }, spec.consumers, threads);

        // observer check size
        final Val oFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                if (q.size() < 0) {
                    oFail.value++;
                }
                Thread.yield();
            }
        }, 1, threads);

        startWaitJoin(stop, threads);

        assertEquals("Observed producer size < 0", 0, pFail.value);
        assertEquals("Observed consumer size < 0", 0, cFail.value);
        assertEquals("Observed observer size < 0", 0, oFail.value);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testSizeGtCapacity() throws Exception
    {
        assumeThat(spec.isBounded(), is(Boolean.TRUE));

        final int capacity = spec.capacity;
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;

        // producer offer and check size
        final Val pFail = new Val();
        List<Thread> threads = new ArrayList<>();
        threads(() -> {
            while (!stop.get()) {
                q.offer(1);

                if (q.size() > capacity) {
                    pFail.value++;
                }
            }
        }, spec.producers, threads);

        // consumer check size and poll
        final Val cFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                if (q.size() > capacity) {
                    cFail.value++;
                }

                q.poll();
                sleepQuietly(1);
            }
        }, 1, threads);

        // observer check size
        final Val oFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                if (q.size() > capacity) {
                    oFail.value++;
                }
                Thread.yield();
            }
        }, 1, threads);

        startWaitJoin(stop, threads);

        assertEquals("Observed producer size > capacity", 0, pFail.value);
        assertEquals("Observed consumer size > capacity", 0, cFail.value);
        assertEquals("Observed observer size > capacity", 0, oFail.value);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPeekEqualsPoll() throws InterruptedException {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue<Integer> q = queue;

        List<Thread> threads = new ArrayList<>();
        threads(() -> {
            int sequence = 0;
            while (!stop.get()) {
                if (q.offer(sequence)) {
                    sequence++;
                }
                Thread.yield();
            }
        }, spec.producers, threads);

        final Val fail = new Val();
        threads(() -> {
            while (!stop.get()) {
                final Integer peekedSequence = q.peek();
                if (peekedSequence == null) {
                    continue;
                }
                if (!peekedSequence.equals(q.poll())) {
                    fail.value++;
                }
            }
        }, 1, threads);

        startWaitJoin(stop, threads);

        assertEquals("Observed peekedSequence is not equal to polledSequence", 0, fail.value);
    }
}
