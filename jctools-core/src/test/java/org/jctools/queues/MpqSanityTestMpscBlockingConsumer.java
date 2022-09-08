package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.jctools.util.TestUtil.TEST_TIMEOUT;
import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class MpqSanityTestMpscBlockingConsumer extends MpqSanityTest
{
    public MpqSanityTestMpscBlockingConsumer(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(0, 1, 1, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(1)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(SIZE)));// MPSC size SIZE
        return list;
    }

    @Test(timeout = TEST_TIMEOUT)
    public void testSpinWaitForUnblockDrainForever() throws InterruptedException {

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
