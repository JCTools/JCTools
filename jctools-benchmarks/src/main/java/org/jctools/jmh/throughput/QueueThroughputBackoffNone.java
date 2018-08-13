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
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
public class QueueThroughputBackoffNone {
    static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
    static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);
    static final Object TEST_ELEMENT = 1;
    Integer element = 1;
    Integer escape;
    Queue<Integer> q;

    @Param(value = { "SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue" })
    String qType;

    @Param(value = { "132000" })
    String qCapacity;

    @Setup()
    public void createQandPrimeCompilation() {
        final String qType = this.qType;

        q = QueueByTypeFactory.createQueue(qType, 128);
        // stretch the queue to the limit, working through resizing and full
        for (int i = 0; i < 128+100; i++) {
            q.offer(element);
        }
        for (int i = 0; i < 128+100; i++) {
            q.poll();

        }
        // make sure the important common case is exercised
        for (int i = 0; i < 20000; i++) {
            q.offer(element);
            q.poll();
        }
        final String qCapacity = this.qCapacity;

        this.q = QueueByTypeFactory.buildQ(qType, qCapacity);
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class PollCounters {
        public long pollsFailed;
        public long pollsMade;
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class OfferCounters {
        public long offersFailed;
        public long offersMade;
    }

    @Benchmark
    @Group("tpt")
    public void offer(OfferCounters counters) {
        if (!q.offer(element)) {
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
    public void poll(PollCounters counters) {
        Integer e = q.poll();
        if (e == null) {
            counters.pollsFailed++;
            backoff();
        } else if (e == TEST_ELEMENT) {
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
        synchronized (q)
        {
            q.clear();
        }
    }
}
