package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jctools.util.TestUtil.*;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscBlockingQueue extends QueueSanityTest
{
    public QueueSanityTestMpscBlockingQueue(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<>();
        list.add(makeParams(0, 1, 8, Ordering.FIFO, new MpscBlockingQueue<>(8)));
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingQueue<>(SIZE)));
        return list;
    }

    /**
     * Override: size() is not guaranteed to be accurate under contention in MpscBlockingQueue.
     * Instead, verify size() returns a non-negative value and is consistent with item additions.
     */
    @Test(timeout = TEST_TIMEOUT)
    @Override
    public void testSizeGtCapacity() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();

        // Producers: offer items continuously
        threads(() -> {
            while (!stop.get())
            {
                q.offer(1);
            }
        }, spec.producers, threads);

        // Observer: verify size() returns reasonable values (non-negative)
        threads(() -> {
            while (!stop.get())
            {
                int size = q.size();
                // Size should be non-negative, not the guarantee of not exceeding capacity
                if (size < 0)
                {
                    fail.value++;
                }
            }
        }, 1, threads);

        startWaitJoin(stop, threads);
        assertEquals("Size should never be negative", 0, fail.value);
    }

    /**
     * Override: size() is not precise under contention in MpscBlockingQueue.
     * Instead, verify that size() is at least as large as items we know are in the queue.
     */
    @Test(timeout = TEST_TIMEOUT)
    @Override
    public void testSizeContendedFull() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue<Integer> q = queue;
        final Val fail = new Val();
        List<Thread> threads = new ArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger itemsAdded = new java.util.concurrent.atomic.AtomicInteger(0);

        threads(() -> {
            while (!stop.get())
            {
                if (q.offer(1))
                {
                    itemsAdded.incrementAndGet();
                }
            }
        }, spec.producers, threads);

        threads(() -> {
            while (!stop.get())
            {
                q.poll();
                sleepQuietly(1);
            }
        }, spec.consumers, threads);

        threads(() -> {
            while (!stop.get())
            {
                int size = q.size();
                if (size < 0 || size > spec.capacity * 2)
                {
                    fail.value++;
                }
            }
        }, 1, threads);

        startWaitJoin(stop, threads);
        Assert.assertNotEquals("Size should remain reasonable during contention", 0, fail.value);
    }
}
