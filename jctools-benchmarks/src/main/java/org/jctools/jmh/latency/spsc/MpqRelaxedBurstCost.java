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
package org.jctools.jmh.latency.spsc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueByTypeFactory;
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

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings("serial")
public class MpqRelaxedBurstCost {
    abstract static class AbstractEvent extends AtomicBoolean {
        abstract void handle();
    }

    static class Go extends AbstractEvent {
        @Override
        void handle() {
            // do nothing
        }
    }

    static class Stop extends AbstractEvent {
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
                while ((e = q.relaxedPoll()) == null);
                e.handle();
            }
        }

    }

    static final Go GO = new Go();
    final Stop stop = new Stop();

    @Param({ "SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue" })
    String qType;

    @Param({ "1", "10", "100", "1000" })
    int burstSize;

    MessagePassingQueue<AbstractEvent> q;

    private Thread consumerThread;
    private Consumer consumer;

    @Setup(Level.Trial)
    public void setupQueueAndConsumer() {
        q = MessagePassingQueueByTypeFactory.createQueue(qType, 128);

        // stretch the queue to the limit, working through resizing and full
        for (int i = 0; i < 128 + 100; i++) {
            q.relaxedOffer(GO);
        }
        for (int i = 0; i < 128 + 100; i++) {
            q.relaxedPoll();
        }
        // make sure the important common case is exercised
        for (int i = 0; i < 20000; i++) {
            q.relaxedOffer(GO);
            q.relaxedPoll();
        }
        q = MessagePassingQueueByTypeFactory.createQueue(qType, 128 * 1024);
        consumer = new Consumer(q);
        consumerThread = new Thread(consumer);
        consumerThread.start();
    }

    @Benchmark
    public void burstCost() {
        final Stop stop = this.stop;
        final int burst = burstSize;
        final MessagePassingQueue<AbstractEvent> q = this.q;
        final Go go = GO;
        stop.lazySet(false);
        for (int i = 0; i < burst - 1; i++) {
            while (!q.relaxedOffer(go));
        }

        while (!q.relaxedOffer(stop));

        while (!stop.get());
    }

    @TearDown
    public void killConsumer() throws InterruptedException {
        consumer.isRunning = false;
        while (!q.relaxedOffer(GO));
        consumerThread.join();
    }
}
