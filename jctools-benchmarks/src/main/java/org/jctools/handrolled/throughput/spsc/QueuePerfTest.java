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

import org.jctools.queues.QueueByTypeFactory;

import java.util.Queue;

public class QueuePerfTest {
    // 15 == 32 * 1024
    public static final int REPETITIONS = Integer.getInteger("reps", 50) * 1000 * 1000;
    public static final Integer TEST_VALUE = 777;

    public static void main(final String[] args) throws Exception {
        System.out.println("capacity:" + QueueByTypeFactory.QUEUE_CAPACITY + " reps:" + REPETITIONS);
        final Queue<Integer> queue = QueueByTypeFactory.createQueue();
        for (int i = 0; i < 100000; i++) {
            queue.offer(TEST_VALUE);
            queue.poll();
        }
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

    private static long performanceRun(int runNumber, Queue<Integer> queue) throws Exception {
        Producer p = new Producer(queue);
        Thread thread = new Thread(p);
        thread.start();// producer will timestamp start

        Integer result;
        int i = REPETITIONS;
        int queueEmpty = 0;
        do {
            while (null == (result = queue.poll())) {
                queueEmpty++;
                Thread.yield();
            }
        } while (0 != --i);
        long end = System.nanoTime();
        
        thread.join();
        long duration = end - p.start;
        long ops = (REPETITIONS * 1000L * 1000L * 1000L) / duration;
        perRunPrint(runNumber, queue, p, result, queueEmpty, ops);
        return ops;
    }

    private static void perRunPrint(int runNumber, Queue<Integer> queue, Producer p, Integer result,
            int queueEmpty, long ops) {
        String qName = queue.getClass().getSimpleName();
        System.out.format("%d - ops/sec=%,d - %s result=%d failed.poll=%d failed.offer=%d\n", runNumber, ops,
                qName, result, queueEmpty, p.queueFull);
    }

    public static class Producer implements Runnable {
        private final Queue<Integer> queue;
        int queueFull = 0;
        long start = 0;

        public Producer(Queue<Integer> queue) {
            this.queue = queue;
        }

        public void run() {
            int i = REPETITIONS;
            int f = 0;
            Queue<Integer> q = queue;
            long s = System.nanoTime();
            do {
                while (!q.offer(TEST_VALUE)) {
                    Thread.yield();
                    f++;
                }
            } while (0 != --i);
            
            queueFull = f;
            start = s;
        }
    }
}
