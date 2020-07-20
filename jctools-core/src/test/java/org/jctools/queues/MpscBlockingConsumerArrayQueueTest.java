package org.jctools.queues;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

public class MpscBlockingConsumerArrayQueueTest {

    @Test
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

        Thread.sleep(10000);

        consumerThread.interrupt();
        consumerThread.join();

        producerThread.interrupt();
        producerThread.join();
    }
}
