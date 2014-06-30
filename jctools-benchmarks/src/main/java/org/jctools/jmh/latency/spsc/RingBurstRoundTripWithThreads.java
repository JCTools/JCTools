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

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;

/**
 * Measure the Round Trip Time between 2 or more threads communicating via chained queues. This is a
 * performance edge case for queues as there is no scope for batching. The size of the batch will hopefully
 * expose near empty performance boundary cases.
 * 
 * @author nitsanw
 * 
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Threads(1)
public class RingBurstRoundTripWithThreads {
    private static final int CHAIN_LENGTH = Integer.getInteger("chain.length", 2);
    private static final int BURST_SIZE = Integer.getInteger("burst.size", 16);
    private static final Integer ONE = 1;
    private final ExecutorService exec = CHAIN_LENGTH > 1 ? Executors.newFixedThreadPool(CHAIN_LENGTH - 1)
            : null;
    private final Queue<Integer>[] chain = new Queue[CHAIN_LENGTH];
    private final Control linkThreadsControl = new Control();
    private final Queue<Integer> start = QueueByTypeFactory.createQueue();
    private final Queue<Integer> end = CHAIN_LENGTH == 1 ? start : QueueByTypeFactory.createQueue();
    private final Link[] links = new Link[CHAIN_LENGTH - 1];

    static class Link implements Runnable {
        final Control linkControl;
        final Queue<Integer> in;
        final Queue<Integer> out;
        CountDownLatch start;
        CountDownLatch stopWriting;
        CountDownLatch done;
        private Exception error;

        public Link(Control control, Queue<Integer> in, Queue<Integer> out) {
            super();
            this.linkControl = control;
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            start.countDown();
            try {
                while (!linkControl.stopMeasurement) {
                    optimizeMe(in, out);
                }
                stopWriting.countDown();
                stopWriting.await();
                in.clear();
            } catch (Exception e) {
                error = e;
            } finally {
                done.countDown();
            }
        }

        private void optimizeMe(Queue<Integer> in, Queue<Integer> out) {
            Integer e;
            while ((e = in.poll()) == null) {
                if (linkControl.stopMeasurement)
                    return;
            }
            out.offer(e);
        }
    }

    @Setup(Level.Trial)
    public void prepareChain() {
        if (CHAIN_LENGTH < 1) {
            throw new IllegalArgumentException("Chain length must be 1 or more");
        }
        if (BURST_SIZE > QueueByTypeFactory.QUEUE_CAPACITY * CHAIN_LENGTH / 2.0) {
            throw new IllegalArgumentException("Batch size exceeds estimated capacity");
        }
        for (int i = 1; i < CHAIN_LENGTH - 1; i++) {
            chain[i] = QueueByTypeFactory.createQueue();
        }
        chain[0] = start;
        chain[CHAIN_LENGTH - 1] = end;
        for (int i = 0; i < CHAIN_LENGTH - 1; i++) {
            links[i] = new Link(linkThreadsControl, chain[i], chain[i + 1]);
        }
    }

    @Setup(Level.Iteration)
    public void startChain() throws InterruptedException {
        if (CHAIN_LENGTH == 1) {
            return;
        }
        linkThreadsControl.stopMeasurement = false;
        CountDownLatch start = new CountDownLatch(CHAIN_LENGTH-1);
        CountDownLatch stopWriting = new CountDownLatch(CHAIN_LENGTH-1);
        CountDownLatch done = new CountDownLatch(CHAIN_LENGTH-1);
        for (int i = 0; i < CHAIN_LENGTH - 1; i++) {
            links[i].start = start;
            links[i].stopWriting = stopWriting;
            links[i].done = done;
            exec.execute(links[i]);
        }
        start.await();
    }

    @Benchmark
    public void ping(Control ctl) {
        for (int i = 0; i < BURST_SIZE; i++) {
            start.offer(ONE);
        }
        for (int i = 0; i < BURST_SIZE; i++) {
            while (!ctl.stopMeasurement && end.poll() == null) {
            }
        }
    }

    @TearDown(Level.Iteration)
    public void emptyQs() throws InterruptedException {
        if (CHAIN_LENGTH > 1) {
            linkThreadsControl.stopMeasurement = true;
            links[0].done.await();
        }
    }

    @TearDown(Level.Trial)
    public void killExecutor() throws InterruptedException {
        if (exec != null) {
            exec.shutdownNow();
            exec.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}