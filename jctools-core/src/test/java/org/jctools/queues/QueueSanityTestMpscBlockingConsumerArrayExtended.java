package org.jctools.queues;

import java.lang.Thread.State;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.jctools.util.TestUtil.Val;
import org.junit.Assert;
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

    @Test(timeout = TEST_TIMEOUT)
    public void testOfferBlockingDrainSemantics() throws Exception
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
            ArrayDeque<Integer> ints = new ArrayDeque<>(1);

            while (!stop.get())
            {
                if (!q.offer(1))
                {
                    fail.value++;
                }

                while (!consumerLock.compareAndSet(true, false));

                try
                {
                    int howMany = q.drain(ints::offer, 1, 1L, DAYS);
                    if (howMany == 0 || howMany != ints.size())
                    {
                        fail.value++;
                    }
                    ints.clear();
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
    public void testBlockingDrainSemantics() throws Exception
    {
        final MpscBlockingConsumerArrayQueue<Integer> q = new MpscBlockingConsumerArrayQueue<>(2);
        ArrayDeque<Integer> ints = new ArrayDeque<>();
        assertEquals(0, q.drain(ints::add, 0, 0, NANOSECONDS));
        assertEquals(0, ints.size());

        q.offer(1);

        assertEquals(0, q.drain(ints::add, 0, 0, NANOSECONDS));
        assertEquals(0, ints.size());
        assertEquals(1, q.drain(ints::add, 1, 0, NANOSECONDS));
        assertEquals((Integer) 1, ints.poll());

        long beforeNanos = System.nanoTime();
        assertEquals(0, q.drain(ints::add, 1, 250L, MILLISECONDS));
        long tookMillis = MILLISECONDS.convert(System.nanoTime() - beforeNanos, NANOSECONDS);
        assertEquals(0, ints.size());
        assertTrue("took " + tookMillis + "ms", 200L < tookMillis && tookMillis < 300L);
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
        testTakeBlocksAndIsInterrupted(PollType.Take);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testPollWithTimeoutBlocksAndIsInterrupted() throws Exception
    {
        testTakeBlocksAndIsInterrupted(PollType.BlockingPoll);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testBlockingDrainWithTimeoutBlocksAndIsInterrupted() throws Exception
    {
        testTakeBlocksAndIsInterrupted(PollType.BlockingDrain);
    }

    private enum PollType {
        BlockingPoll,
        Take,
        BlockingDrain
    }

    private void testTakeBlocksAndIsInterrupted(PollType pollType) throws Exception
    {
        final AtomicBoolean wasInterrupted = new AtomicBoolean();
        final AtomicBoolean interruptedStatusAfter = new AtomicBoolean();
        final MpscBlockingConsumerArrayQueue<Integer> q = new MpscBlockingConsumerArrayQueue<>(1024);
        Thread consumer = new Thread(() -> {
            try
            {
                switch (pollType) {

                case BlockingPoll:
                    q.poll(1L, DAYS);
                    break;
                case Take:
                    q.take();
                    break;
                case BlockingDrain:
                    q.drain(ignored -> {}, 1, 1L, DAYS);
                    break;
                }
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

    @Test
    public void testOfferIfBelowThresholdSemantics() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final MpscBlockingConsumerArrayQueue<Integer> q =
            new MpscBlockingConsumerArrayQueue<>(8);

        final Val fail = new Val();

        Thread t1 = new Thread(() -> {
            while (!stop.get())
            {
                q.poll();

                if (q.size() > 5)
                {
                    fail.value++;
                }
            }
        });

        Thread t2 = new Thread(() -> {
            while (!stop.get())
            {
                q.offerIfBelowThreshold(1, 5);
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(1000);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("Unexpected size observed", 0, fail.value);
    }

    @Test
    public void testOfferWithThreshold()
    {
        MpscBlockingConsumerArrayQueue<Integer> queue = new MpscBlockingConsumerArrayQueue<Integer>(16);
        int i;
        for (i = 0; i < 8; ++i)
        {
            //Offers succeed because current size is below the HWM.
            Assert.assertTrue(queue.offerIfBelowThreshold(i, 8));
        }
        //Not anymore, our offer got rejected.
        Assert.assertFalse(queue.offerIfBelowThreshold(i, 8));
        Assert.assertFalse(queue.offerIfBelowThreshold(i, 7));
        Assert.assertFalse(queue.offerIfBelowThreshold(i, 1));
        Assert.assertFalse(queue.offerIfBelowThreshold(i, 0));

        //Also, the threshold is dynamic and different levels can be set for
        //different task priorities.
        Assert.assertTrue(queue.offerIfBelowThreshold(i, 9));
        Assert.assertTrue(queue.offerIfBelowThreshold(i, 16));
    }

    /**
     * This test demonstrates a race where a producer wins the CAS and writes
     * null to blocked, only to have the consumer overwrite its change. To have
     * it fail consistently, add a 1ms sleep (or a breakpoint) to
     * parkUntilNext(), just before the soBlocked(Thread.currentThread()). If it
     * hits the race writing to the blocked field, one of the threads will spin
     * forever in spinWaitForUnblock().
     */
    @Test(timeout = TEST_TIMEOUT)
    public void testSpinWaitForUnblockForever() throws InterruptedException {

        class Echo<T> implements Runnable{
            private MpscBlockingConsumerArrayQueue<T> source;
            private MpscBlockingConsumerArrayQueue<T> sink;
            private int interations;

            Echo(
                MpscBlockingConsumerArrayQueue<T> source,
                MpscBlockingConsumerArrayQueue<T> sink,
                int interations) {
                    this.source = source;
                    this.sink = sink;
                    this.interations = interations;
            }

            public void run() {
                try {
                    for (int i = 0; i < interations; ++i) {
                        T t;
                        do {
                            t = source.poll(1,  TimeUnit.NANOSECONDS);
                        }
                        while (t == null);

                        sink.put(t);
                    }
                }
                catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        }

        final MpscBlockingConsumerArrayQueue<Object> q1 =
            new MpscBlockingConsumerArrayQueue<>(1024);
        final MpscBlockingConsumerArrayQueue<Object> q2 =
            new MpscBlockingConsumerArrayQueue<>(1024);

        final Thread t1 = new Thread(new Echo<>(q1, q2, 100000));
        final Thread t2 = new Thread(new Echo<>(q2, q1, 100000));

        t1.start();
        t2.start();

        q1.put("x");

        t1.join();
        t2.join();
    }
}
