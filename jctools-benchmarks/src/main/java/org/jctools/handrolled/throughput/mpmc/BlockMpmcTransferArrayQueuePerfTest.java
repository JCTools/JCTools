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
package org.jctools.handrolled.throughput.mpmc;

import org.jctools.queues.MpmcTransferArrayQueue;

public class BlockMpmcTransferArrayQueuePerfTest {
    public static final int REPETITIONS = 50 * 1000 * 100;
    public static final Integer TEST_VALUE = Integer.valueOf(777);

    private static final int concurrency = 2;

    public static void main(final String[] args) throws Exception {
        System.out.println("reps:" + REPETITIONS + "  Concurrency " + concurrency);

        final int warmupRuns = 4;
        final int runs = 5;

        final MpmcTransferArrayQueue<Integer> queue = new MpmcTransferArrayQueue<Integer>(concurrency);
        long average = averageRun(warmupRuns, runs, queue, true, concurrency, REPETITIONS);

        System.out.format("summary,QueuePerfTest,%s %,d\n", queue.getClass().getSimpleName(), average);
    }

    public static long averageRun(int warmUpRuns, int sumCount, MpmcTransferArrayQueue<Integer> queue, boolean showStats, int concurrency, int repetitions) throws Exception {
        int runs = warmUpRuns + sumCount;
        final long[] results = new long[runs];
        for (int i = 0; i < runs; i++) {
            System.gc();
            results[i] = performanceRun(i, queue, showStats, concurrency, repetitions);
        }

        // average results for summary
        long sum = 0;
        for (int i = warmUpRuns; i < runs; i++) {
            sum += results[i];
        }

        return sum/sumCount;
    }

    private static long performanceRun(int runNumber, MpmcTransferArrayQueue<Integer> queue, boolean showStats, int concurrency, int repetitions) throws Exception {

        Producer[] producers = new Producer[concurrency];
        Consumer[] consumers = new Consumer[concurrency];
        Thread[] threads = new Thread[concurrency*2];

        for (int i=0;i<concurrency;i++) {
            producers[i] = new Producer(queue, repetitions);
            consumers[i] = new Consumer(queue, repetitions);
        }

        for (int j=0,i=0;i<concurrency;i++,j+=2) {
            threads[j] = new Thread(producers[i], "Producer " + i);
            threads[j+1] = new Thread(consumers[i], "Consumer " + i);
        }

        for (int i=0;i<concurrency*2;i+=2) {
            threads[i].start();
            threads[i+1].start();
        }

        for (int i=0;i<concurrency*2;i+=2) {
            threads[i].join();
            threads[i+1].join();
        }

        long start = Long.MAX_VALUE;
        long end = -1;

        for (int i=0;i<concurrency;i++) {
            if (producers[i].start < start) {
                start = producers[i].start;
            }

            if (consumers[i].end > end) {
                end = consumers[i].end;
            }
        }


        long duration = end - start;
        long ops = repetitions * 1000000000L / duration;
        String qName = queue.getClass().getSimpleName();

        if (showStats) {
            System.out.format("%d - ops/sec=%,d - %s\n", runNumber, ops, qName);
        }
        return ops;
    }

    public static class Producer implements Runnable {
        private final MpmcTransferArrayQueue<Integer> queue;
        volatile long start;
        private int repetitions;

        public Producer(MpmcTransferArrayQueue<Integer> queue, int repetitions) {
            this.queue = queue;
            this.repetitions = repetitions;
        }

        @Override
        public void run() {
            MpmcTransferArrayQueue<Integer> producer = this.queue;
            int i = this.repetitions;
            this.start = System.nanoTime();

            try {
                do {
                    producer.transfer(TEST_VALUE);
                } while (0 != --i);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                // log.error(e);
            }
        }
    }

    public static class Consumer implements Runnable {
        private final MpmcTransferArrayQueue<Integer> queue;
        Object result;
        volatile long end;
        private int repetitions;

        public Consumer(MpmcTransferArrayQueue<Integer> queue, int repetitions) {
            this.queue = queue;
            this.repetitions = repetitions;
        }

        public void run() {
            MpmcTransferArrayQueue<Integer> consumer = this.queue;
            int i = this.repetitions;

            Integer result = Integer.valueOf(0);
            try {
                do {
                    result = consumer.take();
                } while (0 != --i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            this.result = result;
            this.end = System.nanoTime();
        }
    }
}
