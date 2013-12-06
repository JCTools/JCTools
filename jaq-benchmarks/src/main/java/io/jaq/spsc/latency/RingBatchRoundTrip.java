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
package io.jaq.spsc.latency;

import io.jaq.spsc.SPSCQueueFactory;

import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.logic.Control;

/**
 * Measure the Round Trip Time between 2 or more threads communicating via chained queues. This is a
 * performance edge case for queues as there is no scope for batching. The size of the batch will hopefully
 * expose near empty performance boundary cases.
 * 
 * @author nitsanw
 * 
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(1)
public class RingBatchRoundTrip {
    public final static int BATCH_SIZE = Integer.getInteger("batch.size", 16);
    public final static Integer ONE = 1;
    private static final int CHAIN_LENGTH = Integer.getInteger("chain.length", 2);
    final ExecutorService exec = Executors.newFixedThreadPool(CHAIN_LENGTH - 1);
    Semaphore sam;
    final Queue<Integer>[] chain = new Queue[CHAIN_LENGTH];
    Control control = new Control();

    static class Link implements Runnable {
        final Control control;
        final Queue<Integer> in;
        final Queue<Integer> out;
        Semaphore sam;

        public Link(Control control, Queue<Integer> in, Queue<Integer> out) {
            super();
            this.control = control;
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            sam.release();
            while (!control.stopMeasurement) {
                optimizeMe(in, out);
            }
            sam.release();
        }

        private void optimizeMe(Queue<Integer> in, Queue<Integer> out) {
            Integer e;
            while ((e = in.poll()) == null) {
                if (control.stopMeasurement)
                    return;
            }
            out.offer(e);
        }
    }

    Link[] links = new Link[CHAIN_LENGTH - 1];
    final Queue<Integer> start = SPSCQueueFactory.createQueue();
    final Queue<Integer> end = SPSCQueueFactory.createQueue();

    @Setup(Level.Trial)
    public void prepareChain() {
        for (int i = 1; i < CHAIN_LENGTH - 1; i++) {
            chain[i] = SPSCQueueFactory.createQueue();
        }
        chain[0] = start;
        chain[CHAIN_LENGTH - 1] = end;
        for (int i = 0; i < CHAIN_LENGTH - 1; i++) {
            links[i] = new Link(control, chain[i], chain[i + 1]);
        }
    }

    @Setup(Level.Iteration)
    public void startChain() {
        control.stopMeasurement=false;
        sam = new Semaphore(1 - CHAIN_LENGTH);
        for (int i = 0; i < CHAIN_LENGTH - 1; i++) {
            links[i].sam = sam;
            exec.execute(links[i]);
        }
        sam.release();
        sam.acquireUninterruptibly();
    }

    @GenerateMicroBenchmark
    public void ping(Control cnt) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            start.offer(ONE);
        }
        for (int i = 0; i < BATCH_SIZE; i++) {
            while (!cnt.stopMeasurement && end.poll() == null) {
            }
        }
    }

    @TearDown(Level.Iteration)
    public void emptyQs() {
        control.stopMeasurement=true;
        sam.acquireUninterruptibly(CHAIN_LENGTH - 1);
        for (int i = 0; i < CHAIN_LENGTH; i++) {
            while (chain[i].poll() != null)
                ;
        }
    }

    @TearDown(Level.Trial)
    public void killExecutor() throws InterruptedException {
        exec.shutdownNow();
        exec.awaitTermination(10, TimeUnit.SECONDS);
    }
}