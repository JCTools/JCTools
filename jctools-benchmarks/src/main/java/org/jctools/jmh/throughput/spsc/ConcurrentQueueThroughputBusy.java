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
package org.jctools.jmh.throughput.spsc;

import org.jctools.queues.alt.ConcurrentQueue;
import org.jctools.queues.alt.ConcurrentQueueByTypeFactory;
import org.jctools.queues.alt.ConcurrentQueueConsumer;
import org.jctools.queues.alt.ConcurrentQueueProducer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Control;

import java.util.concurrent.TimeUnit;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class ConcurrentQueueThroughputBusy {
    private final ConcurrentQueue<Integer> q = ConcurrentQueueByTypeFactory.createQueue();
    private final ConcurrentQueueProducer<Integer> producer = q.producer();
    private final ConcurrentQueueConsumer<Integer> consumer = q.consumer();
    private final static Integer ONE = 777;

    @Benchmark
    @Group("tpt")
    @GroupThreads(1)
    public void offer(Control cnt) {
        while (!producer.offer(ONE) && !cnt.stopMeasurement) {
        }
    }

    @Benchmark
    @Group("tpt")
    @GroupThreads(1)
    public void poll(Control cnt, ConsumerMarker cm) {
        while (consumer.poll() == null && !cnt.stopMeasurement) {
        }
    }
    private static ThreadLocal<Object> marker = new ThreadLocal<>();

    @State(Scope.Thread)
    public static class ConsumerMarker {
        public ConsumerMarker() {
            marker.set(this);
        }
    }
    @TearDown(Level.Iteration)
    public void emptyQ() {
        if(marker.get() == null)
            return;
        // sadly the iteration tear down is performed from each participating thread, so we need to guess
        // which is which (can't have concurrent access to poll).
        while (consumer.poll() != null)
            ;
    }
}
