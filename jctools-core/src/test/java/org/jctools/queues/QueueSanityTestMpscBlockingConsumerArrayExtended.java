package org.jctools.queues;

import java.lang.Thread.State;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.jctools.util.TestUtil;
import org.jctools.util.TestUtil.Val;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.*;
import static org.jctools.util.TestUtil.CONCURRENT_TEST_DURATION;
import static org.jctools.util.TestUtil.TEST_TIMEOUT;
import static org.junit.Assert.*;


public class QueueSanityTestMpscBlockingConsumerArrayExtended
{
    @Test
    public void testOfferPollSemantics() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicBoolean consumerLock = new AtomicBoolean(true);
        final Queue<Integer> q = new MpscBlockingConsumerArrayQueue<>(2);
        // fill up the queue
        while (q.offer(1));

        // queue has 2 empty slots
        q.poll();
        q.poll();

        final Val fail = new Val();
        final Runnable runnable = () -> {
            while (!stop.get())
            {
                if (!q.offer(1))
                {
                    fail.value++;
                }

                while (!consumerLock.compareAndSet(true, false));


                if (q.poll() == null)
                {
                    fail.value++;
                }
                consumerLock.lazySet(true);
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

    @Test(timeout = TEST_TIMEOUT)
    public void testPollTimeout() throws InterruptedException {

        final MpscBlockingConsumerArrayQueue<Object> queue =
                new MpscBlockingConsumerArrayQueue<>(128000);

        final Thread consumerThread = new Thread(() -> {
            try {
                while (true) {
                    queue.poll(100, TimeUnit.NANOSECONDS);
                }
            }
            catch (InterruptedException e) {
            }
        });

        consumerThread.start();

        final Thread producerThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                for (int i = 0; i < 10; ++i) {
                    queue.offer("x");
                }
                LockSupport.parkNanos(100000);
            }
        });

        producerThread.start();

        Thread.sleep(CONCURRENT_TEST_DURATION);

        consumerThread.interrupt();
        consumerThread.join();

        producerThread.interrupt();
        producerThread.join();
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testOfferTakeSemantics() throws Exception
    {
        testOfferBlockSemantics(false);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testOfferPollWithTimeoutSemantics() throws Exception
    {
        testOfferBlockSemantics(true);
    }

    private void testOfferBlockSemantics(boolean withTimeout) throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicBoolean consumerLock = new AtomicBoolean(true);
        final MpscBlockingConsumerArrayQueue<Integer> q = new MpscBlockingConsumerArrayQueue<>(2);
        // fill up the queue
        while (q.offer(1));

        // queue has 2 empty slots
        q.poll();
        q.poll();

        final Val fail = new Val();
        final Runnable runnable = () -> {
            while (!stop.get())
            {
                if (!q.offer(1))
                {
                    fail.value++;
                }

                while (!consumerLock.compareAndSet(true, false));

                try
                {
                    Integer take = withTimeout ? q.poll(1L, DAYS) : q.take();
                    if (take == null)
                    {
                        fail.value++;
                    }
                }
                catch (InterruptedException e)
                {
                    fail.value++;
                }
                consumerLock.lazySet(true);
            }
        };
        Thread t1 = new Thread(runnable);
        Thread t2 = new Thread(runnable);

        t1.start();
        t2.start();
        Thread.sleep(CONCURRENT_TEST_DURATION);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("Unexpected offer/poll observed", 0, fail.value);

    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollTimeoutSemantics() throws Exception
    {
        final MpscBlockingConsumerArrayQueue<Integer> q = new MpscBlockingConsumerArrayQueue<>(2);

        assertNull(q.poll(0, NANOSECONDS));

        q.offer(1);
        assertEquals((Integer) 1, q.poll(0, NANOSECONDS));

        long beforeNanos = System.nanoTime();
        assertNull(q.poll(250L, MILLISECONDS));
        long tookMillis = MILLISECONDS.convert(System.nanoTime() - beforeNanos, NANOSECONDS);

        assertTrue("took " + tookMillis + "ms", 200L < tookMillis && tookMillis < 300L);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testTakeBlocksAndIsInterrupted() throws Exception
    {
        testTakeBlocksAndIsInterrupted(false);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollWithTimeoutBlocksAndIsInterrupted() throws Exception
    {
        testTakeBlocksAndIsInterrupted(true);
    }

    private void testTakeBlocksAndIsInterrupted(boolean withTimeout) throws Exception
    {
        final AtomicBoolean wasInterrupted = new AtomicBoolean();
        final AtomicBoolean interruptedStatusAfter = new AtomicBoolean();
        final MpscBlockingConsumerArrayQueue<Integer> q = new MpscBlockingConsumerArrayQueue<>(1024);
        Thread consumer = new Thread(() -> {
            try
            {
                Integer take = withTimeout ? q.poll(1L, DAYS) : q.take();
            }
            catch (InterruptedException e)
            {
                wasInterrupted.set(true);
            }
            interruptedStatusAfter.set(Thread.currentThread().isInterrupted());
        });
        consumer.setDaemon(true);
        consumer.start();
        while(consumer.getState() != State.TIMED_WAITING)
        {
            Thread.yield();
        }
        // If we got here -> thread got to the waiting state -> parked
        consumer.interrupt();
        consumer.join();
        assertTrue(wasInterrupted.get());
        assertFalse(interruptedStatusAfter.get());

        // Queue should remain in original state (empty)
        assertNull(q.poll());
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testTakeSomeElementsThenBlocksAndIsInterrupted() throws Exception
    {
        testTakeSomeElementsThenBlocksAndIsInterrupted(false);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testTakeSomeElementsThenPollWithTimeoutAndIsInterrupted() throws Exception
    {
        testTakeSomeElementsThenBlocksAndIsInterrupted(true);
    }

    private void testTakeSomeElementsThenBlocksAndIsInterrupted(boolean withTimeout) throws Exception
    {
        Val v = new Val();
        final AtomicBoolean wasInterrupted = new AtomicBoolean();
        final MpscBlockingConsumerArrayQueue<Integer> q = new MpscBlockingConsumerArrayQueue<>(1024);
        Thread consumer = new Thread(() -> {
            try
            {
                while (true)
                {
                    Integer take = withTimeout ? q.poll(1L, DAYS) : q.take();
                    assertNotNull(take); // take never returns null
                    assertEquals(take.intValue(), v.value);
                    v.value++;
                }
            }
            catch (InterruptedException e)
            {
                wasInterrupted.set(true);
            }
        });
        consumer.setDaemon(true);
        consumer.start();
        while(consumer.getState() != State.TIMED_WAITING)
        {
            Thread.yield();
        }
        // If we got here -> thread got to the waiting state -> parked
        int someElements = ThreadLocalRandom.current().nextInt(10000);
        for (int i=0;i < someElements; i++)
            while (!q.offer(i));

        while(!q.isEmpty())
        {
            Thread.yield();
        }
        // Eventually queue is drained

        while(consumer.getState() != State.TIMED_WAITING)
        {
            Thread.yield();
        }
        // If we got here -> thread got to the waiting state -> parked

        consumer.interrupt();
        consumer.join();
        assertTrue(wasInterrupted.get());
        assertEquals(someElements, v.value);
    }
}
