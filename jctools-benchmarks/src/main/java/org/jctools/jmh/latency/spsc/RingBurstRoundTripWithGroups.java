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

import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Control;

import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measure the Round Trip Time between 2 or more threads communicating via chained queues. This is a
 * performance edge case for queues as there is less scope for batching. The size of the batch will hopefully
 * expose near empty performance boundary cases.
 * <p>
 * Parameters must obey the following rules:<br>
 * <li>1 thread is the source(running ping), the rest are links</li>
 * <li>The chain length must be equal to the total number of threads</li>
 * <li>Burst size must not exceed the overall capacity of the ring</li>
 * <li>Only a single group of this benchmark can be executed!</li>
 * <li>Iterations must be synchronized(which is the default)</li>
 * <p>
 * To launch this benchmark with chain length 3, run:<br>
 * export JVM_ARGS=-server -XX:+UseCondCardMark -XX:CompileThreshold=100000<br>
 * export QUEUE_ARGS=-Dq.type=3 -Dsparse.shift=0 -Dpow2.capacity=15<br>
 * java $JVM_ARGS $QUEUE_ARGS <b>-Dburst.size=1 -Dchain.length=3</b> -jar target/microbenchmarks.jar <b>-tg
 * 2,1</b> -f 1 ".*.RingBurstRoundTripWithGroups.*"<br>
 * <p>
 * Note that when running this sort of benchmark thread affinity can effect performance significantly. It is
 * therefore recommended you pin the benchmark run to a uniform set of core such that all threads are the same
 * distance from each other. For example, when the chain.length is 2 we can run either pinned to 2 thread
 * running on same core (taskset -c 0,1), or across 2 separate cores (taskset -c 2,4). When running larger
 * chains it is recommended you avoid hyper threaded cores for stability. For example when running with
 * chain.length=4 we can pin the 4 threads to 4 separate cores (taskset -c 1,3,5,7).
 * <p>
 * The instability in the results stems from the different possible layouts chains can have. Consider the
 * case of chain.length=4 using 4 threads on 2 cores the ring layout could be:
 * <li> 0 -> 1 -> 2 -> 3 -> 0: leading to 2 same core transitions + 2 cross core transitions
 * <li> 0 -> 2 -> 1 -> 3 -> 0: leading to 4 cross core transitions
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
    private static final Integer DUMMY_MESSAGE = 1;
    @SuppressWarnings("unchecked")
    private final static Queue<Integer>[] chain = new Queue[CHAIN_LENGTH];
    /**
     * This is a bit annoying, I need the threads to keep their queues, so each thread needs an index. The id
     * is used to pick the in/out queues.
     */
    private final static AtomicInteger idx = new AtomicInteger();
    private final static ThreadLocal<Integer> tlIndex = new ThreadLocal<Integer>() {
        protected Integer initialValue() {
            return idx.getAndIncrement();
        }
    };

    /**
     * Link in the chain passes events from chain[threadIndex] to chain[(id + 1) % CHAIN_LENGTH].
     * <p>
     * Note that while the state is per thread, the thread can change per iteration. We use the above thread
     * id to maintain the same queues are selected per thread.
     */
    @State(Scope.Thread)
    public static class Link {
        final Queue<Integer> in;
        final Queue<Integer> out;

        public Link() {
            int id = tlIndex.get();
            // the old in out, in out
            this.in = chain[id % CHAIN_LENGTH];
            this.out = chain[(id + 1) % CHAIN_LENGTH];
        }

        public void link() {
            // we could use the control here, but there's no reason as it is use externally and we only
            // really want to measure the ping method
            Integer e = in.poll();
            if (e != null) {
                out.offer(e);
            }
        }

        /**
         * We want to always start with an empty inbound. Iteration tear downs are synchronized.
         */
        @TearDown(Level.Iteration)
        public void clear() {
            // SPSC -> consumer must clear the queue
            in.clear();
        }
    }

    /**
     * The source of events in the ring. Sends a burst of events into chain[(id + 1) % CHAIN_LENGTH] and waits
     * until the burst makes it through the ring back to chain[id].
     * <p>
     * Note that while the state is per thread, the thread can change per iteration. We use the above thread
     * id to maintain the same queues are selected per thread.
     */
    @State(Scope.Thread)
    public static class Source {
        final Queue<Integer> start;
        final Queue<Integer> end;

        public Source() {
            int id = tlIndex.get();
            // the source ties the knot in our ring
            this.end = chain[id % CHAIN_LENGTH];
            this.start = chain[(id + 1) % CHAIN_LENGTH];
        }

        public void ping(Control ctl) {
            for (int i = 0; i < BURST_SIZE; i++) {
                start.offer(DUMMY_MESSAGE);
            }
            for (int i = 0; i < BURST_SIZE; i++) {
                while (!ctl.stopMeasurement && end.poll() == null) {
                }
            }
        }

        /**
         * We want to always start with an empty inbound. Iteration tear downs are synchronized.
         */
        @TearDown(Level.Iteration)
        public void clear() {
            // SPSC -> consumer must clear the queue
            end.clear();
        }
    }

    @Setup(Level.Trial)
    public void prepareChain() {
        // can't have group threads set to zero on a method, so can't handle the length of 1 case
        if (CHAIN_LENGTH < 2) {
            throw new IllegalArgumentException("Chain length must be 2 or more");
        }
        // This is an estimate, but for bounded queues if the burst size is more than actual ring capacity
        // the benchmark will hang/
        if (BURST_SIZE > QueueByTypeFactory.QUEUE_CAPACITY * CHAIN_LENGTH >> 1) {
            throw new IllegalArgumentException("Batch size exceeds estimated capacity");
        }
        // initialize the chain
        for (int i = 0; i < CHAIN_LENGTH; i++) {
            chain[i] = QueueByTypeFactory.createQueue();
        }
    }

    @Benchmark
    @Group("ring")
    @GroupThreads(1)
    public void ping(Control ctl, Source s) {
        s.ping(ctl);
    }

    @Benchmark
    @Group("ring")
    @GroupThreads(1)
    public void loop(Link l) {
        l.link();
    }
}
