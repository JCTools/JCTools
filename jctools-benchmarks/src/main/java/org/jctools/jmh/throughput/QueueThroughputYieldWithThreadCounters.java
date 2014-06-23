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
package org.jctools.jmh.throughput;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.jctools.queues.TypeQueueFactory;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class QueueThroughputYieldWithThreadCounters {
    private static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
    private static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);
    private static final Integer ONE = 777;
    public final Queue<Integer> q = TypeQueueFactory.createQueue();

    @AuxCounters
    @State(Scope.Thread)
    public static class OpCounters {
        public int pollFail, offerFail;

        @Setup(Level.Iteration)
        public void clean() {
            pollFail = offerFail = 0;
        }
    }

    private static ThreadLocal<Object> marker = new ThreadLocal<>();

    @State(Scope.Thread)
    public static class ConsumerMarker {
        public ConsumerMarker() {
            marker.set(this);
        }
    }

    @Benchmark
    @Group("tpt")
    public void offer(OpCounters counters) {
        if (!q.offer(ONE)) {
            counters.offerFail++;
            Thread.yield();
        } 
        if (DELAY_PRODUCER != 0) {
            Blackhole.consumeCPU(DELAY_PRODUCER);
        }
    }

    @Benchmark
    @Group("tpt")
    public void poll(OpCounters counters, ConsumerMarker cm) {
        if (q.poll() == null) {
            counters.pollFail++;
            Thread.yield();
        } 
        if (DELAY_CONSUMER != 0) {
            Blackhole.consumeCPU(DELAY_CONSUMER);
        }
    }

    @TearDown(Level.Iteration)
    public void emptyQ() {
        if (marker.get() == null)
            return;
        // sadly the iteration tear down is performed from each participating thread, so we need to guess
        // which is which (can't have concurrent access to poll).
        while (q.poll() != null)
            ;
    }
}
