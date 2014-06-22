/*
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

import java.util.Queue;

import org.jctools.queues.TypeQueueFactory;

public class BusyQueuePerfTest {
    // 15 == 32 * 1024
    public static final int REPETITIONS = Integer.getInteger("reps", 50) * 1000 * 1000;
    public static final Integer TEST_VALUE = Integer.valueOf(777);

    public static void main(final String[] args) throws Exception {
        System.out.println("capacity:" + TypeQueueFactory.QUEUE_CAPACITY + " reps:" + REPETITIONS);
        final Queue<Integer> queue = TypeQueueFactory.createQueue();

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
        System.out.format("summary,BusyQueuePerfTest,%s,%d\n", queue.getClass().getSimpleName(), sum / 10);
    }

    private static long performanceRun(final int runNumber, final Queue<Integer> queue) throws Exception {
        final long start = System.nanoTime();
        final Producer p = new Producer(queue);
        final Thread thread = new Thread(p);
        thread.start();

        Integer result;
        int i = REPETITIONS;
        int f = 0;
        do {
            while (null == (result = queue.poll())) {
                f++;
            }
        } while (0 != --i);

        thread.join();

        final long duration = System.nanoTime() - start;
        final long ops = (REPETITIONS * 1000L * 1000L * 1000L) / duration;
        System.out.format("%d - ops/sec=%,d - %s result=%d failed.poll=%d failed.offer=%d\n",
                Integer.valueOf(runNumber), Long.valueOf(ops),
                queue.getClass().getSimpleName(), result,f,p.fails);
        return ops;
    }

    public static class Producer implements Runnable {
        private final Queue<Integer> queue;
        int fails=0;
        public Producer(final Queue<Integer> queue) {
            this.queue = queue;
        }

        public void run() {
            final Queue<Integer> q = queue;
            int i = REPETITIONS;
            int f=0;
            do {
                while (!q.offer(TEST_VALUE)) {
                    f++;
                }
            } while (0 != --i);
            fails = f;
        }
    }
}
