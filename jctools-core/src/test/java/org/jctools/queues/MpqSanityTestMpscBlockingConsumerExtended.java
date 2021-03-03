package org.jctools.queues;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class MpqSanityTestMpscBlockingConsumerExtended
{
    /**
     * This test demonstrates a race described here: https://github.com/JCTools/JCTools/issues/339
     * You will need to debug to observe the spin.
     */
    @Test
    public void testSpinWaitForUnblockForeverFill() throws InterruptedException {

        class Echo<T> implements Runnable {
            final MpscBlockingConsumerArrayQueue<T> source;
            final MpscBlockingConsumerArrayQueue<T> sink;
            final int interations;
            final int batch;

            Echo(
                    MpscBlockingConsumerArrayQueue<T> source,
                    MpscBlockingConsumerArrayQueue<T> sink,
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

        final MpscBlockingConsumerArrayQueue<Object> q1 =
                new MpscBlockingConsumerArrayQueue<>(1024);
        final MpscBlockingConsumerArrayQueue<Object> q2 =
                new MpscBlockingConsumerArrayQueue<>(1024);

        final Thread t1 = new Thread(new Echo<>(q1, q2, 100000, 10));
        final Thread t2 = new Thread(new Echo<>(q2, q1, 100000, 10));

        t1.start();
        t2.start();

        for (int j = 0; j < 10; j++) q1.put("x");

        t1.join();
        t2.join();
    }
}
