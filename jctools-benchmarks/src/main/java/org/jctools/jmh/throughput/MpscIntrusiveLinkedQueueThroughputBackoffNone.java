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

import org.jctools.queues.intrusive.MpscIntrusiveLinkedQueue;
import org.jctools.queues.intrusive.Node;
import org.jctools.queues.intrusive.NodeImpl;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
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

import java.util.concurrent.TimeUnit;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
public class MpscIntrusiveLinkedQueueThroughputBackoffNone {
    private static final long DELAY_PRODUCER = Long.getLong("delay.p", 0L);
    private static final long DELAY_CONSUMER = Long.getLong("delay.c", 0L);

    MpscIntrusiveLinkedQueue q;

    @Setup()
    public void createQandPrimeCompilation() {
        q = new MpscIntrusiveLinkedQueue();
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
        if (!q.offer(new NodeImpl())) {
            counters.offersFailed++;
            backoff();
        } else {
            counters.offersMade++;
        }
        if (DELAY_PRODUCER != 0) {
            Blackhole.consumeCPU(DELAY_PRODUCER);
        }
    }

    @Benchmark
    @Group("tpt")
    public void poll(PollCounters counters) {
        Node n = q.poll();
        if (n == null) {
            counters.pollsFailed++;
            backoff();
        } else {
            counters.pollsMade++;
        }
        if (DELAY_CONSUMER != 0) {
            Blackhole.consumeCPU(DELAY_CONSUMER);
        }
    }

    protected void backoff() {
    }

    // iteration tear down is performed for each thread, only consumer should clear queue
    @TearDown(Level.Iteration)
    public void consumerClearQueue() {
        synchronized (q)
        {
            q.clear();
        }
    }
}
