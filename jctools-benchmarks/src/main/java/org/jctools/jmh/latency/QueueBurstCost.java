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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings("serial")
public class QueueBurstCost {
    private static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
    private static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);

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

    static class ConsumerPad {
        public long p40, p41, p42, p43, p44, p45, p46;
        public long p30, p31, p32, p33, p34, p35, p36, p37;
    }
    static class ConsumerFields extends ConsumerPad {
        Queue<AbstractEvent> q;
        volatile boolean isRunning = true;
        CountDownLatch stopped;
    }
    static class Consumer extends ConsumerFields implements Runnable {
        public long p40, p41, p42, p43, p44, p45, p46;
        public long p30, p31, p32, p33, p34, p35, p36, p37;

        public Consumer(Queue<AbstractEvent> q) {
            this.q = q;
        }
        @Override
        public void run() {
            final Queue<AbstractEvent> q = this.q;
            while (isRunning) {
                consume(q);
            }
            stopped.countDown();
        }

        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        private void consume(Queue<AbstractEvent> q) {
            AbstractEvent e = null;
            if ((e = q.poll()) == null) {
                return;
            }
            if (DELAY_CONSUMER != 0) {
                Blackhole.consumeCPU(DELAY_CONSUMER);
            }
            e.handle();
        }

    }

    Go GO = new Go();

    @Param({ "SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue" })
    String qType;

    @Param({ "100" })
    int burstSize;

    @Param("1")
    int consumerCount;

    @Param("true")
    boolean warmup;

    @Param(value = { "132000" })
    String qCapacity;

    Queue<AbstractEvent> q;

    private ExecutorService consumerExecutor;
    private Consumer consumer;

    @Setup(Level.Trial)
    public void setupQueue() {
        if (warmup) {
            q = QueueByTypeFactory.createQueue(qType, 128);

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
        }
        q = QueueByTypeFactory.buildQ(qType, qCapacity);
        consumer = new Consumer(q);
        consumerExecutor = Executors.newFixedThreadPool(consumerCount);
    }

    @Setup(Level.Iteration)
    public void startConsumers() {
        consumer.isRunning = true;
        consumer.stopped = new CountDownLatch(consumerCount);
        for(int i=0;i<consumerCount;i++) consumerExecutor.execute(consumer);

    }

    @TearDown(Level.Iteration)
    public void stopConsumers() throws InterruptedException {
        consumer.isRunning = false;
        consumer.stopped.await();
    }
    @TearDown(Level.Trial)
    public void stopExecutor() throws InterruptedException {
        consumerExecutor.shutdown();
    }


        @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void burstCost(Stop stop) {
        final int burst = burstSize;
        final Queue<AbstractEvent> q = this.q;
        final Go go = GO;
        stop.lazySet(false);
        sendBurst(q, burst, go, stop);

        waitForIt(stop);
    }

    private void waitForIt(Stop stop) {
        while (!stop.get());
    }

    private void sendBurst(Queue<AbstractEvent> q, int burst, Go go, Stop stop) {
        for (int i = 0; i < burst - 1; i++) {
            while (!q.offer(go));
            if (DELAY_PRODUCER != 0) {
                Blackhole.consumeCPU(DELAY_PRODUCER);
            }
        }

        while (!q.offer(stop));
    }


}
