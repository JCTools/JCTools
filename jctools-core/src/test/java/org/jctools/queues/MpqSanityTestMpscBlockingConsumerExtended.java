package org.jctools.queues;

import org.jctools.queues.MessagePassingQueue.Supplier;
import org.jctools.queues.atomic.MpscBlockingConsumerAtomicArrayQueue;
import org.jctools.queues.atomic.unpadded.MpscBlockingConsumerAtomicUnpaddedArrayQueue;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.unpadded.MpscBlockingConsumerUnpaddedArrayQueue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.jctools.util.TestUtil.TEST_TIMEOUT;
import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscBlockingConsumerExtended
{
    final Supplier<MessagePassingBlockingQueue<Object>> factory;
    public MpqSanityTestMpscBlockingConsumerExtended(Supplier<MessagePassingBlockingQueue<Object>> factory)
    {
        this.factory = factory;
    }

    @Parameterized.Parameters
    public static Collection<Supplier<MessagePassingBlockingQueue<Object>>> parameters()
    {
        ArrayList<Supplier<MessagePassingBlockingQueue<Object>>> list = new ArrayList<>();
        list.add(() -> new MpscBlockingConsumerArrayQueue<>(1024));
        list.add(() -> new MpscBlockingConsumerUnpaddedArrayQueue<>(1024));
        list.add(() -> new MpscBlockingConsumerAtomicArrayQueue<>(1024));// MPSC size 1
        list.add(() -> new MpscBlockingConsumerAtomicUnpaddedArrayQueue<>(1024));// MPSC size 1
        return list;
    }

    /**
     * This test demonstrates a race described here: https://github.com/JCTools/JCTools/issues/339
     * You will need to debug to observe the spin.
     */
    @Test
    public void testSpinWaitForUnblockForeverFill() throws InterruptedException {

        class Echo<T> implements Runnable {
            final MessagePassingBlockingQueue<T> source;
            final MessagePassingBlockingQueue<T> sink;
            final int interations;
            final int batch;

            Echo(
                MessagePassingBlockingQueue<T> source,
                MessagePassingBlockingQueue<T> sink,
                    int iterations,
                    int batch) {
                this.source = source;
                this.sink = sink;
                this.interations = iterations;
                this.batch = batch;
            }

            public void run() {
                Queue<T> batchContainer = new ArrayDeque<>(batch);
                try {
                    for (int i = 0; i < interations; ++i) {
                        for (int j = 0; j < batch; j++) {
                            T t;
                            do {
                                t = source.poll(1, TimeUnit.NANOSECONDS);
                            }
                            while (t == null);
                            batchContainer.add(t);
                        }
                        do {
                            sink.fill(() -> batchContainer.poll(), batchContainer.size());
                        } while (!batchContainer.isEmpty());
                    }
                }
                catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        }

        final MessagePassingBlockingQueue<Object> q1 = factory.get();
        final MessagePassingBlockingQueue<Object> q2 = factory.get();

        final Thread t1 = new Thread(new Echo<>(q1, q2, 100000, 10));
        final Thread t2 = new Thread(new Echo<>(q2, q1, 100000, 10));

        t1.start();
        t2.start();

        for (int j = 0; j < 10; j++) q1.put("x");

        t1.join();
        t2.join();
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testSpinWaitForUnblockDrainForever() throws InterruptedException {

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
                ArrayDeque<T> ints = new ArrayDeque<>();
                try {
                    for (int i = 0; i < interations; ++i) {
                        T t;
                        do {
                            source.drain(ints::offer, 1, 1, NANOSECONDS);
                            t = ints.poll();
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

        final MessagePassingBlockingQueue<Object> q1 = factory.get();
        final MessagePassingBlockingQueue<Object> q2 = factory.get();


        final Thread t1 = new Thread(new Echo<>(q1, q2, 100000));
        final Thread t2 = new Thread(new Echo<>(q2, q1, 100000));

        t1.start();
        t2.start();

        q1.put("x");

        t1.join();
        t2.join();
    }
}
