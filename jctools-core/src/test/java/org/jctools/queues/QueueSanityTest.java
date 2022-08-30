package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.util.Pow2;
import org.jctools.util.TestUtil;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.jctools.queues.MessagePassingQueue.UNBOUNDED_CAPACITY;
import static org.jctools.queues.matchers.Matchers.emptyAndZeroSize;
import static org.jctools.util.TestUtil.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

public abstract class QueueSanityTest
{
    public static final int SIZE = 8192 * 2;

    protected final Queue<Integer> queue;
    protected final ConcurrentQueueSpec spec;

    public QueueSanityTest(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        this.queue = queue;
        this.spec = spec;
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

    @Test
    public void testQueueProgressIndicators()
    {
        assumeThat(queue, is(instanceOf(QueueProgressIndicators.class)));
        QueueProgressIndicators q = (QueueProgressIndicators) queue;
        // queue is empty
        assertEquals(q.currentConsumerIndex(), q.currentProducerIndex());
        queue.offer(1);
        assertEquals(q.currentConsumerIndex() + 1, q.currentProducerIndex());
        queue.poll();
        assertEquals(q.currentConsumerIndex(), q.currentProducerIndex());
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBeforePeek() throws Exception
    {
        testHappensBefore0(true);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testHappensBeforePoll() throws Exception
    {
        testHappensBefore0(false);

    }

    private void testHappensBefore0(boolean peek) throws InterruptedException
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();
        threads(() -> {
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
        }, spec.producers, threads);

        threads(() -> {
            while (!stop.get())
            {
                for (int i = 0; i < 10; i++)
                {
                    Val v = peek ? (Val) q.peek() : (Val) q.poll();
                    if (v != null && v.value == 0)
                    {
                        // assert peek/poll visible values are never uninitialized
                        fail.value = 1;
                        stop.set(true);
                        break;
                    }
                    else if (peek && v != null && v != q.poll())
                    {
                        // assert peek visible values are same as poll
                        fail.value = 2;
                        stop.set(true);
                        break;
                    }
                }
            }
        }, 1, threads);

        startWaitJoin(stop, threads);
        assertEquals("reordering detected", 0, fail.value);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testSize() throws Exception
    {
        final int capacity = !spec.isBounded() ? Integer.MAX_VALUE : spec.capacity;

        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();
        // each thread is adding and removing 1 element
        threads(() -> {
            try
            {
                while (!stop.get())
                {
                    // conditional is required when threads > capacity
                    if (q.offer(1)) ;
                    while (q.poll() == null && !stop.get()) ;
                }
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                fail.value++;
            }
        }, spec.isMpmc() ? 0 : 1, threads);
        int producersConsumers = threads.size();
        // observer
        threads(() -> {
            final int max = Math.min(producersConsumers, capacity);
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
    public void testSizeContendedFull() throws Exception
    {
        assumeThat(spec.isBounded(), is(Boolean.TRUE));
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        // this is fragile
        int capacity = spec.capacity;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();

        threads(() -> {
            while (!stop.get())
            {
                q.offer(1);
            }
        }, spec.producers, threads);

        threads(() -> {
            while (!stop.get())
            {
                q.poll();
                // slow down the consumer, this will make the queue mostly full
                sleepQuietly(1);
            }
        }, spec.consumers, threads);
        // observer
        threads(() -> {
            while (!stop.get())
            {
                int size = q.size();
                if (size > capacity)
                {
                    fail.value++;
                }
            }
        }, 1, threads);

        startWaitJoin(stop, threads);

        assertEquals("Observed no element in non-empty queue", 0, fail.value);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPeekAfterIsEmpty1() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
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
        final Queue<Integer> q = queue;
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
        final Queue<Integer> q = queue;
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
        final Queue<Integer> q = queue;
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
        final Queue<Integer> q = queue;
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
        final Queue<Integer> q = queue;
        final Val fail = new Val();
        testIsEmptyInvariant(stop, q, fail, () -> {
            while (!stop.get())
            {
                // can the consumer progress "passed" the producer and confuse `size`?
                q.poll();
                if (q.size() != 0 && q.poll() == null)
                {
                    fail.value++;
                }
            }
        });
    }

    private void testIsEmptyInvariant(AtomicBoolean stop, Queue<Integer> q, Val fail, Runnable consumerLoop)
        throws InterruptedException
    {
        List<Thread> threads = new ArrayList<>();

        threads(() -> {
            while (!stop.get())
            {
                q.offer(1);
                // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                Thread.yield();
            }
        }, spec.producers, threads);
        threads(consumerLoop, 1, threads);

        startWaitJoin(stop, threads);

        assertEquals("Observed no element in non-empty queue", 0, fail.value);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollOrderContendedFull() throws Exception
    {
        assumeThat(spec.isBounded(), is(Boolean.TRUE));
        assumeThat(spec.ordering, is(Ordering.FIFO));

        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();

        final AtomicInteger pThreadId = new AtomicInteger();
        threads(() -> {
            // store the thread id in the top 8 bits
            int pId = pThreadId.getAndIncrement() << 24;

            int i = 0;
            while (!stop.get() && i < Integer.MAX_VALUE >>> 8)
            {
                // clear the top 8 bits
                int nextVal = (i << 8) >>> 8;
                // set the pid in the top 8 bits
                if (q.offer(nextVal ^ pId))
                {
                    i++;
                }
            }
        }, spec.producers, threads);
        int producers = threads.size();
        assertThat("The thread ID scheme above doesn't work for more than 256 threads", producers, lessThan(256));
        threads(() -> {
            Integer[] lastPolledSequence = new Integer[producers];
            while (!stop.get())
            {
                sleepQuietly(1);
                final Integer polledSequenceAndTid = q.poll();
                if (polledSequenceAndTid == null)
                {
                    continue;
                }
                int pTid = polledSequenceAndTid >>> 24;
                int polledSequence = (polledSequenceAndTid << 8) >>> 8;
                if (lastPolledSequence[pTid] != null && polledSequence - lastPolledSequence[pTid] < 0)
                {
                    fail.value++;
                }

                lastPolledSequence[pTid] = polledSequence;
            }
        }, spec.consumers, threads);

        startWaitJoin(stop, threads);

        assertEquals("Polled elements out of order", 0, fail.value);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPeekOrderContendedFull() throws Exception
    {
        // only for multi consumers as we need a separate peek/poll thread here
        assumeThat(spec.isBounded() && (spec.isMpmc() || spec.isSpmc()), is(Boolean.TRUE));
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();

        final AtomicInteger pThreadId = new AtomicInteger();
        threads(() -> {
            // store the thread id in the top 8 bits
            int pId = pThreadId.getAndIncrement() << 24;

            int i = 0;
            while (!stop.get() && i < Integer.MAX_VALUE >>> 8)
            {
                // clear the top 8 bits
                int nextVal = (i << 8) >>> 8;
                // set the pid in the top 8 bits
                if (q.offer(nextVal ^ pId))
                {
                    i++;
                }
            }
        }, spec.producers, threads);
        int producers = threads.size();
        assertThat("The thread ID scheme above doesn't work for more than 256 threads", producers, lessThan(256));

        threads(() -> {
            while (!stop.get())
            {
                q.poll();
                // slow down the consumer, this will make the queue mostly full
                sleepQuietly(1);
            }
        }, spec.consumers, threads);
        // observer
        threads(() -> {
            Integer[] lastPeekedSequence = new Integer[producers];
            while (!stop.get())
            {
                final Integer peekedSequenceAndTid = q.peek();
                if (peekedSequenceAndTid == null)
                {
                    continue;
                }
                int pTid = peekedSequenceAndTid >>> 24;
                int peekedSequence = (peekedSequenceAndTid << 8) >>> 8;

                if (lastPeekedSequence[pTid] != null && peekedSequence - lastPeekedSequence[pTid] < 0)
                {
                    fail.value++;
                }

                lastPeekedSequence[pTid] = peekedSequence;
            }
        }, 1, threads);

        startWaitJoin(stop, threads);

        assertEquals("Peeked elements out of order", 0, fail.value);
    }

    @Test
    public void testIterator()
    {
        assumeThat(queue, instanceOf(SupportsIterator.class));
        assumeThat(queue, instanceOf(MessagePassingQueue.class));

        int capacity = ((MessagePassingQueue) queue).capacity();
        int insertLimit = (capacity == UNBOUNDED_CAPACITY) ? 128 : capacity;

        for (int i = 0; i < insertLimit; i++)
        {
            queue.offer(i);
        }

        Iterator<Integer> iterator = queue.iterator();
        for (int i = 0; i < insertLimit; i++)
        {
            assertEquals(Integer.valueOf(i), iterator.next());
        }
        assertTrue((capacity == UNBOUNDED_CAPACITY) || !iterator.hasNext());

        queue.poll(); // drop 0
        queue.offer(insertLimit); // add capacity
        iterator = queue.iterator();
        for (int i = 1; i <= insertLimit; i++)
        {
            assertEquals(Integer.valueOf(i), iterator.next());
        }
        assertTrue((capacity == UNBOUNDED_CAPACITY) || !iterator.hasNext());
    }

    @Test
    public void testIteratorHasNextConcurrentModification()
    {
        assumeThat(queue, instanceOf(SupportsIterator.class));
        assumeThat(queue, instanceOf(MessagePassingQueue.class));
        int capacity = ((MessagePassingQueue) queue).capacity();
        if (capacity != UNBOUNDED_CAPACITY)
        {
            assumeThat(capacity, greaterThanOrEqualTo(2));
        }
        //There may be gaps in the elements returned by the iterator,
        //but hasNext needs to be reliable even if the elements are consumed between hasNext() and next().
        queue.offer(0);
        queue.offer(1);
        Iterator<Integer> iter = queue.iterator();
        assertThat(iter.hasNext(), is(true));
        queue.poll();
        queue.poll();
        assertThat(queue.isEmpty(), is(true));
        assertThat(iter.hasNext(), is(true));
        assertThat(iter.next(), is(0));
        assertThat(iter.hasNext(), is(false));
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testSizeLtZero() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        final List<Thread> threads = new ArrayList<>();

        // producer check size and offer
        final Val pFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                if (q.size() < 0)
                {
                    pFail.value++;
                }

                q.offer(1);
                TestUtil.sleepQuietly(1);
            }
        }, spec.producers, threads);

        // consumer poll and check size
        final Val cFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                q.poll();

                if (q.size() < 0)
                {
                    cFail.value++;
                }
            }
        }, spec.consumers, threads);

        // observer check size
        final Val oFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                if (q.size() < 0)
                {
                    oFail.value++;
                }
                TestUtil.sleepQuietly(1);
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
        final Queue<Integer> q = queue;
        final List<Thread> threads = new ArrayList<>();

        // producer offer and check size
        final Val pFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                q.offer(1);

                if (q.size() > capacity)
                {
                    pFail.value++;
                }
            }
        }, spec.producers, threads);

        // consumer check size and poll
        final Val cFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                if (q.size() > capacity)
                {
                    cFail.value++;
                }

                q.poll();
                TestUtil.sleepQuietly(1);
            }
        }, spec.consumers, threads);

        // observer check size
        final Val oFail = new Val();
        threads(() -> {
            while (!stop.get())
            {
                if (q.size() > capacity)
                {
                    oFail.value++;
                }
                TestUtil.sleepQuietly(1);
            }
        }, 1, threads);

        startWaitJoin(stop, threads);

        assertEquals("Observed producer size > capacity", 0, pFail.value);
        assertEquals("Observed consumer size > capacity", 0, cFail.value);
        assertEquals("Observed observer size > capacity", 0, oFail.value);
    }

}
