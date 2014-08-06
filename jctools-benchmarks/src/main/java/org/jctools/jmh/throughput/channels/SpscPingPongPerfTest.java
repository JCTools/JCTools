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
package org.jctools.jmh.throughput.channels;

import org.jctools.channels.Channel;
import org.jctools.channels.ChannelConsumer;
import org.jctools.channels.ChannelProducer;
import org.jctools.channels.ChannelReceiver;
import org.jctools.channels.spsc.SpscChannel;
import org.jctools.queues.alt.ConcurrentQueue;
import org.jctools.queues.alt.ConcurrentQueueByTypeFactory;
import org.jctools.queues.alt.ConcurrentQueueConsumer;
import org.jctools.queues.alt.ConcurrentQueueProducer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.Control;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * JMH Test to measure throughput for a tiny message with an SPSC Channel.
 */
@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
public class SpscPingPongPerfTest {

    private ByteBuffer buffer;
    private Channel<Ping> channel;
    private ChannelProducer<Ping> producer;
    private ChannelConsumer consumer;
    private ChannelReceiver<Ping> receiver;

    // Deliberately not a local to avoid constant folding.
    private long writeValue = 1L;

    @Setup
    public void setup(final Blackhole blackhole) {
        receiver = new ChannelReceiver<Ping>() {
            @Override
            public void accept(Ping element) {
                blackhole.consume(element.getValue());
            }
        };
        buffer = ByteBuffer.allocateDirect(1024 * 1024);
        channel = new SpscChannel<Ping>(buffer, 100, Ping.class);
        producer = channel.producer();
        consumer = channel.consumer(receiver);
    }

    // Writing stores a value, since this is what a real program would have to do.
    @Benchmark
    @Group("tpt")
    @GroupThreads(1)
    public void write(Control cnt) {
        Ping element = producer.currentElement();
        while (producer.claim() && !cnt.stopMeasurement) {
            element.setValue(writeValue);

            if (!producer.commit()) {
                break;
            }
            Thread.yield();
        }
    }

    @Benchmark
    @Group("tpt")
    @GroupThreads(1)
    public void read(Control cnt) {
        while (consumer.read() && !cnt.stopMeasurement) {
            Thread.yield();
        }
    }

}
