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

import org.jctools.channels.*;
import org.jctools.channels.spsc.SpscChannel;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeDirectByteBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.Control;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * JMH Test to measure throughput for a tiny message with an SPSC Channel.
 *
 * To run this benchmark:
 *
 * java -jar target/microbenchmarks.jar ".*.SpscPingPongThroughputTest.*"
 */
@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@Fork(1)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class SpscChannelThroughputTest {

	@Param({"32000"})
	private int capacity;
	
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
        int requiredBufferSize = OffHeapFixedMessageSizeRingBuffer.getRequiredBufferSize(capacity, PortableJvmInfo.CACHE_LINE_SIZE);
        buffer = UnsafeDirectByteBuffer.allocateAlignedByteBuffer(requiredBufferSize, PortableJvmInfo.CACHE_LINE_SIZE);
        channel = new SpscChannel<Ping>(buffer, capacity, Ping.class);
        producer = channel.producer();
        consumer = channel.consumer(receiver);
    }

    @Benchmark
    @Group("busy")
    @GroupThreads(1)
    public void writeBusy(Control cnt) {
        ChannelProducer<Ping> lProducer = producer;
		while (!lProducer.claim()) {
        	if (cnt.stopMeasurement) {
        		return;// drop out of spinning if the benchmark iteration is done
        	}
        }
    	Ping element = lProducer.currentElement();
        element.setValue(writeValue);
        lProducer.commit();
    }

    @Benchmark
    @Group("busy")
    @GroupThreads(1)
    public void readBusy(Control cnt) {
        while (!consumer.read() && !cnt.stopMeasurement) {

        }
    }

    @Benchmark
    @Group("backoffOneNano")
    @GroupThreads(1)
    public void write(Control cnt) {
    	ChannelProducer<Ping> lProducer = producer;
		while (!lProducer.claim()) {
        	if (cnt.stopMeasurement) {
        		return;// drop out of spinning if the benchmark iteration is done
        	}
        	LockSupport.parkNanos(1L);
        }
    	Ping element = lProducer.currentElement();
        element.setValue(writeValue);
        lProducer.commit();
    }

    @Benchmark
    @Group("backoffOneNano")
    @GroupThreads(1)
    public void read(Control cnt) {
        while (!consumer.read() && !cnt.stopMeasurement) {
            LockSupport.parkNanos(1L);
        }
    }
}
