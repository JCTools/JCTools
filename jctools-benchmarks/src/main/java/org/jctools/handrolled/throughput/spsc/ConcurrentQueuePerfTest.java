/*
 * Copyright 2012 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.handrolled.throughput.spsc;

import org.jctools.queues.alt.ConcurrentQueue;
import org.jctools.queues.alt.ConcurrentQueueByTypeFactory;
import org.jctools.queues.alt.ConcurrentQueueConsumer;
import org.jctools.queues.alt.ConcurrentQueueProducer;

public class ConcurrentQueuePerfTest {
    // 15 == 32 * 1024
    public static final int REPETITIONS = Integer.getInteger("reps", 50) * 1000 * 1000;
    public static final Integer TEST_VALUE = 777;

    public static void main(final String[] args) throws Exception {
        System.out.println("capacity:" + ConcurrentQueueByTypeFactory.QUEUE_CAPACITY + " reps:" + REPETITIONS);
        final ConcurrentQueue<Integer> queue = ConcurrentQueueByTypeFactory.createQueue();

        final long[] results = new long[20];
        for (int i = 0; i < 20; i++) {
            System.gc();
            results[i] = performanceRun(i, queue);
        }
        // only average last 10 results for summary
        long sum = 0;
        for (int i = 10; i < 20; i++) {
            sum += results[i];
        }
        System.out.format("summary,QueuePerfTest,%s,%d\n", queue.getClass().getSimpleName(), sum / 10);
    }

    private static long performanceRun(int runNumber, ConcurrentQueue<Integer> queue) throws Exception {
        Producer p = new Producer(queue);
        Thread thread = new Thread(p);
        thread.start(); // producer will timestamp start

        ConcurrentQueueConsumer<Integer> consumer = queue.consumer();
        Integer result;
        int i = REPETITIONS;
        int queueEmpty = 0;
        do {
            while (null == (result = consumer.poll())) {
                queueEmpty++;
                Thread.yield();
            }
        } while (0 != --i);
        long end = System.nanoTime();

        thread.join();
        long duration = end - p.start;
        long ops = (REPETITIONS * 1000L * 1000L * 1000L) / duration;
        String qName = queue.getClass().getSimpleName();
        System.out.format("%d - ops/sec=%,d - %s result=%d failed.poll=%d failed.offer=%d\n", runNumber, ops,
                qName, result, queueEmpty, p.queueFull);
        return ops;
    }

    public static class Producer implements Runnable {
        private final ConcurrentQueue<Integer> queue;
        int queueFull = 0;
        long start;

        public Producer(ConcurrentQueue<Integer> queue) {
            this.queue = queue;
        }

        public void run() {
            ConcurrentQueueProducer<Integer> producer = queue.producer();
            int i = REPETITIONS;
            int f = 0;
            long s = System.nanoTime();
            do {
                while (!producer.offer(TEST_VALUE)) {
                    Thread.yield();
                    f++;
                }
            } while (0 != --i);
            queueFull = f;
            start = s;
        }
    }
}
