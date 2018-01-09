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
package org.jctools.channels.spsc;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueByTypeFactory;
import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class QueueThroughputBaseline {
    Integer ONE = 777;
    @Param(value = { "SpscArrayQueue" })
    String qType;

    @Param(value = { "128000" })
    String qCapacity;
    MessagePassingQueue<Integer> q;

    @Setup()
    public void createQandPrimeCompilation() {
        final String qType = this.qType;

        q = MessagePassingQueueByTypeFactory.createQueue(qType, 128);
        // stretch the queue to the limit, working through resizing and full
        for (int i = 0; i < 128+100; i++) {
            q.relaxedOffer(ONE);
        }
        for (int i = 0; i < 128+100; i++) {
            q.relaxedPoll();

        }
        // make sure the important common case is exercised
        for (int i = 0; i < 20000; i++) {
            q.relaxedOffer(ONE);
            q.relaxedPoll();
        }
        final String qCapacity = this.qCapacity;

        this.q = MessagePassingQueueByTypeFactory.buildQ(qType, qCapacity);
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
        if (!q.relaxedOffer(ONE)) {
            counters.offersFailed++;
        } else {
            counters.offersMade++;
        }

    }

    @Benchmark
    @Group("tpt")
    public void poll(PollCounters counters, ConsumerMarker cm, Blackhole bh) {
        Integer e = q.relaxedPoll();
        if (e == null) {
            counters.pollsFailed++;
        } else {
            bh.consume(e);
            counters.pollsMade++;
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
