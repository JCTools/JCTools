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
package org.jctools.jmh.latency;

import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueByTypeFactory;
import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings("serial")
public class MpqBurstCost {
    abstract static class AbstractEvent extends AtomicBoolean {
        abstract void handle();
    }

    static class Go extends AbstractEvent {
        @Override
        void handle() {
            // do nothing
        }
    }

    @State(Scope.Thread)
    public static class Stop extends AbstractEvent {
        @Override
        void handle() {
            lazySet(true);
        }
    }

    static class Consumer implements Runnable {
        final MessagePassingQueue<AbstractEvent> q;
        volatile boolean isRunning = true;

        public Consumer(MessagePassingQueue<AbstractEvent> q) {
            this.q = q;
        }

        @Override
        public void run() {
            final MessagePassingQueue<AbstractEvent> q = this.q;
            while (isRunning) {
                AbstractEvent e = null;
                if ((e = q.relaxedPoll()) == null) {
                    continue;
                }
                e.handle();
            }
        }

    }

    static final Go GO = new Go();

    @Param({ "SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue" })
    String qType;

    @Param({ "100" })
    int burstSize;

    @Param("1")
    int consumerCount;

    MessagePassingQueue<AbstractEvent> q;

    private Thread[] consumerThreads;
    private Consumer consumer;

    @Setup(Level.Trial)
    public void setupQueueAndConsumers() {
        q = MessagePassingQueueByTypeFactory.createQueue(qType, 128);

        // stretch the queue to the limit, working through resizing and full
        for (int i = 0; i < 128 + 100; i++) {
            q.offer(GO);
        }
        for (int i = 0; i < 128 + 100; i++) {
            q.poll();
        }
        // make sure the important common case is exercised
        for (int i = 0; i < 20000; i++) {
            q.offer(GO);
            q.poll();
        }
        q = MessagePassingQueueByTypeFactory.createQueue(qType, 128 * 1024);
        consumer = new Consumer(q);
        consumerThreads = new Thread[consumerCount];
        for (int i = 0; i < consumerCount; i++) {
            consumerThreads[i] = new Thread(consumer);
            consumerThreads[i].start();
        }
    }

    @Benchmark
    public void burstCost(Stop stop) {
        final int burst = burstSize;
        final MessagePassingQueue<AbstractEvent> q = this.q;
        final Go go = GO;
        stop.lazySet(false);
        for (int i = 0; i < burst - 1; i++) {
            while (!q.offer(go))
                ;
        }

        while (!q.offer(stop))
            ;

        while (!stop.get())
            ;
    }

    @TearDown
    public void killConsumer() throws InterruptedException {
        consumer.isRunning = false;
        for (int i = 0; i < consumerCount; i++) {
            consumerThreads[i].join();
        }
    }
}
