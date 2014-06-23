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

import java.util.concurrent.TimeUnit;

import org.jctools.queues.TypeConcurrentQueueFactory;
import org.jctools.queues.alt.ConcurrentQueue;
import org.jctools.queues.alt.ConcurrentQueueConsumer;
import org.jctools.queues.alt.ConcurrentQueueProducer;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measure cost of offer/poll on single thread.
 * 
 * @author nitsanw
 * 
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class ConcQOfferPoll {
    private static final int BURST_SIZE = Integer.getInteger("burst.size", 1);
    private static final Integer DUMMY_MESSAGE = 1;
    private final ConcurrentQueue<Integer> q = TypeConcurrentQueueFactory.createQueue();
    private final ConcurrentQueueConsumer<Integer> c = q.consumer();
    private final ConcurrentQueueProducer<Integer> p = q.producer();
    

    @Benchmark
    public void offerAndPoll() {
        for (int i = 0; i < BURST_SIZE; i++) {
            p.offer(DUMMY_MESSAGE);
        }
        for (int i = 0; i < BURST_SIZE; i++) {
            c.poll();
        }
    }
}