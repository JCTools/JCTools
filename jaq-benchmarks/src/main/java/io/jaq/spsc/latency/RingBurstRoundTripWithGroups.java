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

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.logic.Control;

/**
 * Measure the Round Trip Time between 2 or more threads communicating via chained queues. This is a
 * performance edge case for queues as there is no scope for batching. The size of the batch will hopefully
 * expose near empty performance boundary cases.
 * <p>
 * Only a single group of this benchmark can be executed!
 * 
 * @author nitsanw
 * 
 */
@State(Scope.Group)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class RingBurstRoundTripWithGroups {
    private static final int CHAIN_LENGTH = Integer.getInteger("chain.length", 2);
    private static final int BURST_SIZE = Integer.getInteger("burst.size", 1);
    private static final Integer ONE = 1;
    private final static Queue<Integer>[] chain = new Queue[CHAIN_LENGTH];
    // This is a bit annoying, I need the threads to keep their queues, so each thread needs an index
    // the id is used to pick the in/out queues.
    final static AtomicInteger idx = new AtomicInteger();
    final static ThreadLocal<Integer> tlIndex = new ThreadLocal<Integer>() {
        protected Integer initialValue() {
            return idx.getAndIncrement();
        }
    };

    // Note that while the state is per thread, the thread can change per iteration.
    @State(Scope.Thread)
    public static class Link {
        final Queue<Integer> in;
        final Queue<Integer> out;

        public Link() {
            super();
            int id = tlIndex.get();
            // the old in out, in out
            this.in = chain[id];
            this.out = chain[(id + 1) % CHAIN_LENGTH];
        }

        public void link(Control ctl) {
            Integer e = null;
            while (!ctl.stopMeasurement && (e = in.poll()) == null) {
            }
            if (e != null) {
                out.offer(e);
            }
        }

        // we want to always start with an empty inbound
        @TearDown(Level.Iteration)
        public void clear() {
            in.clear();
        }
    }

    @State(Scope.Thread)
    public static class Source {
        final Queue<Integer> start;
        final Queue<Integer> end;

        public Source() {
            int id = tlIndex.get();
            // the source ties the knot in our ring
            this.end = chain[id];
            this.start = chain[(id + 1) % CHAIN_LENGTH];
        }

        public void ping(Control ctl) {
            for (int i = 0; i < BURST_SIZE; i++) {
                start.offer(ONE);
            }
            for (int i = 0; i < BURST_SIZE; i++) {
                while (!ctl.stopMeasurement && end.poll() == null) {
                }
            }
        }

        // we want to always start with an empty inbound
        @TearDown(Level.Iteration)
        public void clear() {
            end.clear();
        }
    }

    @Setup(Level.Trial)
    public void prepareChain() {
        init();
    }

    private static void init() {
        if (CHAIN_LENGTH < 2) {
            throw new IllegalArgumentException("Chain length must be 1 or more");
        }
        if (BURST_SIZE > SPSCQueueFactory.QUEUE_CAPACITY * CHAIN_LENGTH / 2.0) {
            throw new IllegalArgumentException("Batch size exceeds estimated capacity");
        }
        for (int i = 0; i < CHAIN_LENGTH; i++) {
            chain[i] = SPSCQueueFactory.createQueue();
        }
    }

    @GenerateMicroBenchmark
    @Group("ring")
    @GroupThreads(1)
    public void ping(Control ctl, Source s) {
        s.ping(ctl);
    }

    @GenerateMicroBenchmark
    @Group("ring")
    @GroupThreads(1)
    public void loop(Control ctl, Link l) {
        l.link(ctl);
    }
}