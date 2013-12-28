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
package io.jaq.jmh.spsc.throughput;

import io.jaq.spsc.SPSCQueueFactory;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Group;
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
import org.openjdk.jmh.logic.Control;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class QueueThroughputBusyWithThreadCounters {
    public final Queue<Integer> q = SPSCQueueFactory.createQueue();
    public final static Integer ONE = 777;

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

    @GenerateMicroBenchmark
    @Group("tpt")
    public void offer(Control cnt, OpCounters counters) {
        if (!q.offer(ONE)) {
            counters.pollFail++;
        }
    }

    @GenerateMicroBenchmark
    @Group("tpt")
    public void poll(Control cnt, OpCounters counters, ConsumerMarker cm) {
        if (q.poll() == null) {
            counters.pollFail++;
        }
    }

    @TearDown(Level.Iteration)
    public void emptyQ() {
        if(marker.get() == null)
            return;
        // sadly the iteration tear down is performed from each participating thread, so we need to guess
        // which is which (can't have concurrent access to poll).
        while (q.poll() != null)
            ;
    }
}
