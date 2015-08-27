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

import java.util.concurrent.TimeUnit;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.SpscArrayQueue;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Group;
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

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class MpqDrainFillThroughputBackoffNone {
    private static final Integer ONE = 777;
    SpscArrayQueue<Integer> q;
    private Integer escape;
    // @Param(value = { "SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue" })
    // String qType;
    @Param(value = { "132000" })
    int qCapacity;

    @Setup()
    public void createQandPrimeCompilation() {
        q = new SpscArrayQueue<Integer>(qCapacity);
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class PollCounters {
        public int pollsFailed;
        public int pollsMade;

        @Setup(Level.Iteration)
        public void clean() {
            pollsFailed = pollsMade = 0;
        }
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class OfferCounters {
        public int offersFailed;
        public int offersMade;

        @Setup(Level.Iteration)
        public void clean() {
            offersFailed = offersMade = 0;
        }
    }

    private static ThreadLocal<Object> marker = new ThreadLocal<Object>();

    @State(Scope.Thread)
    public static class ConsumerMarker {
        public ConsumerMarker() {
            marker.set(this);
        }
    }

    @Benchmark
    @Group("normal")
    public void fill(final OfferCounters counters) {
        long filled = q.fill(new MessagePassingQueue.Supplier<Integer>() {
            
            @Override
            public Integer get() {
                counters.offersMade++;
                return ONE;
            }
        });
        if (filled == 0) {
            counters.offersFailed++;
            backoff();
        }
    }

    @Benchmark
    @Group("normal")
    public void drain(final PollCounters counters, ConsumerMarker cm) {
        long drained = q.drain(new MessagePassingQueue.Consumer<Integer>() {
            @Override
            public void accept(Integer e) {
                if (e == ONE) {
                    counters.pollsMade++;
                } else {
                    escape = e;
                }
            }
        });
        if (drained == 0) {
            counters.pollsFailed++;
            backoff();
        }
    }

    @TearDown(Level.Iteration)
    public void emptyQ() {
        if (marker.get() == null)
            return;
        // sadly the iteration tear down is performed from each participating
        // thread, so we need to guess
        // which is which (can't have concurrent access to poll).
        while (q.poll() != null)
            ;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    protected void backoff() {
    }
}
