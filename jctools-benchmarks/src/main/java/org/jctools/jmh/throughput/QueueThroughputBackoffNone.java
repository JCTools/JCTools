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

import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class QueueThroughputBackoffNone {
    private static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
    private static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);
    Integer ONE = 777;
    private Integer escape;
    @Param(value = { "SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue" })
    String qType;
    @Param(value = { "132000" })
    int qCapacity;
    Queue<Integer> q;

    @Setup()
    public void createQandPrimeCompilation() {
        q = QueueByTypeFactory.createQueue(qType, 128);
        
        // stretch the queue to the limit, working through resizing and full
        for (int i = 0; i < 128+100; i++) {
            q.offer(ONE);
        }
        for (int i = 0; i < 128+100; i++) {
            q.poll();
        }
        // make sure the important common case is exercised
        for (int i = 0; i < 20000; i++) {
            q.offer(ONE);
            q.poll();
        }
        q = QueueByTypeFactory.createQueue(qType, qCapacity);
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
    @Group("tpt")
    public void offer(OfferCounters counters) {
        if (!q.offer(ONE)) {
            counters.offersFailed++;
            backoff();
        } else {
            counters.offersMade++;
        }
        if (DELAY_PRODUCER != 0) {
            Blackhole.consumeCPU(DELAY_PRODUCER);
        }
    }

    protected void backoff() {
    }

    @Benchmark
    @Group("tpt")
    public void poll(PollCounters counters, ConsumerMarker cm) {
        Integer e = q.poll();
        if (e == null) {
            counters.pollsFailed++;
            backoff();
        } else if (e == ONE) {
            counters.pollsMade++;
        } else {
            escape = e;
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
        q.clear();
    }
}
