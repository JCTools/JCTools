package org.jctools.queues;

import java.lang.Thread.State;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntFunction;

import org.jctools.queues.atomic.MpscBlockingConsumerAtomicArrayQueue;
import org.jctools.queues.atomic.unpadded.MpscBlockingConsumerAtomicUnpaddedArrayQueue;
import org.jctools.queues.unpadded.MpscBlockingConsumerUnpaddedArrayQueue;
import org.jctools.util.TestUtil.Val;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static java.util.concurrent.TimeUnit.*;
import static org.jctools.util.TestUtil.CONCURRENT_TEST_DURATION;
import static org.jctools.util.TestUtil.TEST_TIMEOUT;
import static org.junit.Assert.*;


@RunWith(Parameterized.class)
public class QueueSanityTestMpscBlockingConsumerArrayExtended
{
    final IntFunction<MessagePassingBlockingQueue<Object>> factory;

    public QueueSanityTestMpscBlockingConsumerArrayExtended(IntFunction<MessagePassingBlockingQueue<Object>> factory)
    {
        this.factory = factory;
    }

    @Parameterized.Parameters
    public static Collection<IntFunction<MessagePassingBlockingQueue<Object>>> parameters()
    {
        ArrayList<IntFunction<MessagePassingBlockingQueue<Object>>> list = new ArrayList<>();
        list.add(MpscBlockingConsumerArrayQueue::new);
        list.add(MpscBlockingConsumerUnpaddedArrayQueue::new);
        list.add(MpscBlockingConsumerAtomicArrayQueue::new);
        list.add(MpscBlockingConsumerAtomicUnpaddedArrayQueue::new);
        return list;
    }

    @Test
    public void testOfferPollSemantics() throws Exception
    {
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicBoolean consumerLock = new AtomicBoolean(true);
        final Queue<Object> q = factory.apply(2);
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

        final MessagePassingBlockingQueue<Object> queue = factory.apply(128000);

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
        final MessagePassingBlockingQueue<Object> q = factory.apply(2);
        // fill up the queue
        while (q.offer(1));

        // queue has 2 empty slots
        q.poll();
        q.poll();

        final Val fail = new Val();
        final Runnable runnable = () -> {
            ArrayDeque<Object> ints = new ArrayDeque<>(1);

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
        final MessagePassingBlockingQueue<Object> q = factory.apply(2);
        ArrayDeque<Object> ints = new ArrayDeque<>();
        assertEquals(0, q.drain(ints::add, 0, 0, NANOSECONDS));
        assertEquals(0, ints.size());

        q.offer(1);

        assertEquals(0, q.drain(ints::add, 0, 0, NANOSECONDS));
        assertEquals(0, ints.size());
        assertEquals(1, q.drain(ints::add, 1, 0, NANOSECONDS));
        assertEquals(1, ints.poll());

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
        final MessagePassingBlockingQueue<Object> q = factory.apply(2);
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
                    Object take = withTimeout ? q.poll(1L, DAYS) : q.take();
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
        final MessagePassingBlockingQueue<Object> q = factory.apply(2);

        assertNull(q.poll(0, NANOSECONDS));

        q.offer(1);
        assertEquals(1, q.poll(0, NANOSECONDS));

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
        final MessagePassingBlockingQueue<Object> q = factory.apply(1024);
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
        final MessagePassingBlockingQueue<Object> q = factory.apply(1024);
        Thread consumer = new Thread(() -> {
            try
            {
                while (true)
                {
                    Object take = withTimeout ? q.poll(1L, DAYS) : q.take();
                    assertNotNull(take); // take never returns null
                    assertEquals(((Integer) take).intValue(), v.value);
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
        final MessagePassingBlockingQueue<Object> q =
            factory.apply(8);

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
                ((OfferIfBelowThreshold<Object>)q).offerIfBelowThreshold(1, 5);
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
        MessagePassingBlockingQueue<Object> queue = factory.apply(16);
        OfferIfBelowThreshold<Object> q = (OfferIfBelowThreshold<Object>)queue;
        int i;
        for (i = 0; i < 8; ++i)
        {
            //Offers succeed because current size is below the HWM.
            Assert.assertTrue(q.offerIfBelowThreshold(i, 8));
        }
        //Not anymore, our offer got rejected.
        Assert.assertFalse(q.offerIfBelowThreshold(i, 8));
        Assert.assertFalse(q.offerIfBelowThreshold(i, 7));
        Assert.assertFalse(q.offerIfBelowThreshold(i, 1));
        Assert.assertFalse(q.offerIfBelowThreshold(i, 0));

        //Also, the threshold is dynamic and different levels can be set for
        //different task priorities.
        Assert.assertTrue(q.offerIfBelowThreshold(i, 9));
        Assert.assertTrue(q.offerIfBelowThreshold(i, 16));
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
            private MessagePassingBlockingQueue<T> source;
            private MessagePassingBlockingQueue<T> sink;
            private int interations;

            Echo(
                MessagePassingBlockingQueue<T> source,
                MessagePassingBlockingQueue<T> sink,
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

        final MessagePassingBlockingQueue<Object> q1 = factory.apply(1024);
        final MessagePassingBlockingQueue<Object> q2 = factory.apply(1024);

        final Thread t1 = new Thread(new Echo<>(q1, q2, 100000));
        final Thread t2 = new Thread(new Echo<>(q2, q1, 100000));

        t1.start();
        t2.start();

        q1.put("x");

        t1.join();
        t2.join();
    }
}
