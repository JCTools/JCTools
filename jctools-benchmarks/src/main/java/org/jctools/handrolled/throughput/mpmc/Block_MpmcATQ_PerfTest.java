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

import org.jctools.queues.MpmcArrayTransferQueue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * These tests aren't to describe "real world" performance, but to derive comparisons between different implementations, and because of
 * something called OSR, you CANNOT depend on these tests to describe what they are testing in the "real world".
 *
 *
 * see:
 * http://psy-lob-saw.blogspot.de/2016/02/wait-for-it-counteduncounted-loops.html
 * http://www.cliffc.org/blog/2011/11/22/what-the-heck-is-osr-and-why-is-it-bad-or-good/
 *
 * We can, however try to make this as "real world" as possible by telling the JVM to compile the bytecode. Use these VM arguments.
 *   -XX:-TieredCompilation -Xcomp -server
 *
 *  TODO: replace these tests with JMH for more accurate tests
 */
@SuppressWarnings("Duplicates")
public class Block_MpmcATQ_PerfTest {
    public static final int REPETITIONS = 50 * 1000 * 100;
    public static final Integer TEST_VALUE = Integer.valueOf(777);

    private static final int producers = 2;
    private static final int consumers = 2;

    private static final int bestRunsToAverage = 4;
    private static final int runs = 10;
    private static final int warmups = 3;
    private static final boolean showStats = true;

    public static void main(final String[] args) throws Exception {
        System.out.format("reps: %,d  Concurrency: " + producers + " producers, " + consumers + " consumers\n", REPETITIONS);
        new Block_MpmcATQ_PerfTest().run(REPETITIONS, producers, consumers, warmups, runs, bestRunsToAverage, showStats);
    }

    public
    void run(final int repetitions,
             final int producers,
             final int consumers,
             final int warmups,
             final int runs,
             final int bestRunsToAverage,
             final boolean showStats) throws Exception {

        final MpmcArrayTransferQueue<Integer> queue = new MpmcArrayTransferQueue<Integer>();

        for (int i = 0; i < warmups; i++) {
            performanceRun(i, queue,
                           false,
                           producers,
                           consumers, repetitions);
        }

        final Long[] results = new Long[runs];
        for (int i = 0; i < runs; i++) {
            System.gc();
            results[i] = performanceRun(i, queue,
                                        showStats,
                                        producers,
                                        consumers, repetitions);
        }

        // average best results for summary
        List<Long> list = Arrays.asList(results);
        Collections.sort(list);

        long sum = 0;
        // ignore the highest one
        int limit = runs - 1;
        for (int i = limit - bestRunsToAverage; i < limit; i++) {
            sum += list.get(i);
        }

        long average = sum / bestRunsToAverage;
        System.out.format("summary,%s,%s,%dP/%dC,%,d\n", this.getClass().getSimpleName(), queue.getClass().getSimpleName(), producers,
                          consumers, average);
    }

    /**
     * Benchmarks how long it takes to push X number of items total. If there are is 1P and 1C, then X items will be sent from a
     * producer to a consumer. Of there are NP and NC threads, then X/N (for a total of X) items will be sent.
     */
    private
    long performanceRun(final int runNumber,
                        final MpmcArrayTransferQueue<Integer> queue,
                        final boolean showStats,
                        final int producersCount,
                        final int consumersCount,
                        int repetitions) throws Exception {

        // make sure it's evenly divisible by both producers and consumers
        final int adjusted = (repetitions / producersCount / consumersCount) * 2;

        int pRepetitions = adjusted * producersCount;
        int cRepetitions = adjusted * consumersCount;


        Producer[] producers = new Producer[producersCount];
        Consumer[] consumers = new Consumer[consumersCount];

        Thread[] pThreads = new Thread[producersCount];
        Thread[] cThreads = new Thread[consumersCount];

        for (int i=0;i<producersCount;i++) {
            producers[i] = new Producer(queue, pRepetitions);
        }
        for (int i=0;i<consumersCount;i++) {
            consumers[i] = new Consumer(queue, cRepetitions);
        }

        for (int i=0;i<producersCount;i++) {
            pThreads[i] = new Thread(producers[i], "Producer " + i);
        }
        for (int i=0;i<consumersCount;i++) {
            cThreads[i] = new Thread(consumers[i], "Consumer " + i);
        }

        for (int i=0;i<producersCount;i++) {
            pThreads[i].start();
        }
        for (int i=0;i<consumersCount;i++) {
            cThreads[i].start();
        }

        for (int i=0;i<producersCount;i++) {
            pThreads[i].join();
        }
        for (int i=0;i<consumersCount;i++) {
            cThreads[i].join();
        }

        long start = Long.MAX_VALUE;
        long end = -1;

        for (int i=0;i<producersCount;i++) {
            if (producers[i].start < start) {
                start = producers[i].start;
            }
        }
        for (int i=0;i<consumersCount;i++) {
            if (consumers[i].end > end) {
                end = consumers[i].end;
            }
        }

        long duration = end - start;
        long ops = repetitions * 1000000000L / duration;

        if (showStats) {
            System.out.format("%d - ops/sec=%,d\n", runNumber, ops);
        }
        return ops;
    }

    public static class Producer implements Runnable {
        private final MpmcArrayTransferQueue<Integer> queue;
        volatile long start;
        private int repetitions;

        public Producer(MpmcArrayTransferQueue<Integer> queue, int repetitions) {
            this.queue = queue;
            this.repetitions = repetitions;
        }

        @Override
        public void run() {
            MpmcArrayTransferQueue<Integer> producer = this.queue;
            int i = this.repetitions;
            this.start = System.nanoTime();

            try {
                do {
                    producer.transfer(TEST_VALUE);
                } while (0 != --i);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class Consumer implements Runnable {
        private final MpmcArrayTransferQueue<Integer> queue;
        Object result;
        volatile long end;
        private int repetitions;

        public Consumer(MpmcArrayTransferQueue<Integer> queue, int repetitions) {
            this.queue = queue;
            this.repetitions = repetitions;
        }

        public void run() {
            MpmcArrayTransferQueue<Integer> consumer = this.queue;
            int i = this.repetitions;

            Integer result = Integer.valueOf(0);
            try {
                do {
                    result = consumer.take();
                } while (0 != --i);
            } catch (Exception e) {
                e.printStackTrace();
            }

            this.result = result;
            this.end = System.nanoTime();
        }
    }
}
