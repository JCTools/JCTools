package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestMpmcUnboundedXadd extends MpqSanityTest
{
    public MpqSanityTestMpmcUnboundedXadd(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 0)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 0)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 1)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 1)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 2)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 2)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 3)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 3)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(1, 4)));
        list.add(makeParams(0, 0, 0, Ordering.FIFO, new MpmcUnboundedXaddArrayQueue<>(16, 4)));
        return list;
    }

    @Test
    public void peekShouldNotSeeFutureElements() throws InterruptedException
    {
        MpmcUnboundedXaddArrayQueue<Long> xaddQueue = (MpmcUnboundedXaddArrayQueue) this.queue;
        Assume.assumeTrue("The queue need to pool some chunk to run this test", xaddQueue.maxPooledChunks() > 0);
        CountDownLatch stop = new CountDownLatch(1);
        Producer producer = new Producer(xaddQueue, stop);
        producer.start();
        Consumer consumer = new Consumer(xaddQueue, stop);
        consumer.start();
        Peeker peeker = new Peeker(xaddQueue, stop, false);
        peeker.start();
        try
        {
            stop.await(2, TimeUnit.SECONDS);
        }
        finally
        {
            stop.countDown();
        }
        final String error = peeker.error;
        if (error != null)
        {
            Assert.fail(error);
        }
        producer.join();
        consumer.join();
        peeker.join();
    }

    @Test
    public void relaxedPeekShouldNotSeeFutureElements() throws InterruptedException
    {
        MpmcUnboundedXaddArrayQueue<Long> xaddQueue = (MpmcUnboundedXaddArrayQueue) this.queue;
        Assume.assumeTrue("The queue need to pool some chunk to run this test", xaddQueue.maxPooledChunks() > 0);
        CountDownLatch stop = new CountDownLatch(1);
        Producer producer = new Producer(xaddQueue, stop);
        producer.start();
        Consumer consumer = new Consumer(xaddQueue, stop);
        consumer.start();
        Peeker peeker = new Peeker(xaddQueue, stop, true);
        peeker.start();
        try
        {
            stop.await(2, TimeUnit.SECONDS);
        }
        finally
        {
            stop.countDown();
        }
        final String error = peeker.error;
        if (error != null)
        {
            Assert.fail(error);
        }
        producer.join();
        consumer.join();
        peeker.join();
    }

    private static class Producer extends Thread
    {
        final CountDownLatch stop;
        final MpUnboundedXaddArrayQueue<?, Long> messageQueue;
        long sequence = 0;

        Producer(MpUnboundedXaddArrayQueue<?, Long> messageQueue, CountDownLatch stop)
        {
            this.messageQueue = messageQueue;
            this.stop = stop;
        }

        @Override
        public void run()
        {
            final int chunkSize = messageQueue.chunkSize();
            final int capacity = chunkSize * messageQueue.maxPooledChunks();

            while (stop.getCount() > 0)
            {
                if (messageQueue.offer(sequence))
                {
                    sequence++;
                }

                while (messageQueue.size() >= capacity - chunkSize)
                {
                    if (stop.getCount() == 0) {
                        return;
                    }
                    Thread.yield();
                }
            }
        }
    }

    private static class Consumer extends Thread
    {

        final MpUnboundedXaddArrayQueue<?, Long> messageQueue;
        final CountDownLatch stop;

        private Consumer(MpUnboundedXaddArrayQueue<?, Long> messageQueue, CountDownLatch stop)
        {
            this.messageQueue = messageQueue;
            this.stop = stop;

        }

        @Override
        public void run()
        {
            final int chunkSize = messageQueue.chunkSize();

            while (stop.getCount() > 0)
            {
                messageQueue.poll();

                while (messageQueue.size() < chunkSize)
                {
                    if (stop.getCount() == 0) {
                        return;
                    }
                    Thread.yield();
                }
            }
        }

    }

    private static class Peeker extends Thread
    {

        final MessagePassingQueue<Long> messageQueue;
        final CountDownLatch stop;
        long lastPeekedSequence;
        volatile String error;
        final boolean relaxed;

        private Peeker(MessagePassingQueue<Long> messageQueue, CountDownLatch stop, boolean relaxed)
        {
            this.messageQueue = messageQueue;
            this.stop = stop;
            this.relaxed = relaxed;
            setPriority(MIN_PRIORITY);
            error = null;
        }

        @Override
        public void run()
        {
            final boolean relaxed = this.relaxed;
            while (stop.getCount() > 0)
            {
                final Long peekedSequence = relaxed ? messageQueue.relaxedPeek() : messageQueue.peek();
                if (peekedSequence == null)
                {
                    continue;
                }

                if (peekedSequence < lastPeekedSequence)
                {
                    error =
                        String.format("peekedSequence %s, lastPeekedSequence %s", peekedSequence, lastPeekedSequence);
                    stop.countDown();
                }

                lastPeekedSequence = peekedSequence;
            }
        }
    }
}
