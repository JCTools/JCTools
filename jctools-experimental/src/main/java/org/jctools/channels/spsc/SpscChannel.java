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

import org.jctools.channels.Channel;
import org.jctools.channels.ChannelConsumer;
import org.jctools.channels.ChannelProducer;
import org.jctools.channels.ChannelReceiver;
import org.jctools.channels.mapping.Mapper;

import java.nio.ByteBuffer;

import static org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer.getRequiredBufferSize;

public final class SpscChannel<E> implements Channel<E> {

    private final int elementSize;
    private final Mapper<E> mapper;
    private final ByteBuffer buffer;
    private final int capacity;
    private final SpscChannelProducer<E> producer;

    /**
	 * This is to be used for an IPC queue with the direct buffer used being a memory
	 * mapped file.
	 *
	 * @param buffer
	 * @param capacity
	 */
    // TODO: take an initialize parameter
	public SpscChannel(final ByteBuffer buffer, final int capacity, final Class<E> type) {
        this.capacity = capacity;
        this.buffer = buffer;
        mapper = new Mapper<>(type, false);
        elementSize = mapper.getSizeInBytes();

        checkSufficientCapacity();
        checkByteBuffer();

        producer = mapper.newProducer(buffer, capacity, elementSize);
    }

    private void checkByteBuffer() {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Channels only work with direct or memory mapped buffers");
        }
    }

    private void checkSufficientCapacity() {
        final int requiredCapacityInBytes = getRequiredBufferSize(capacity, elementSize);
        if (buffer.capacity() < requiredCapacityInBytes) {
            throw new IllegalArgumentException("Failed to meet required capacity in bytes: " + requiredCapacityInBytes);
        }
    }

    public ChannelConsumer consumer(ChannelReceiver<E> receiver) {
        return mapper.newConsumer(buffer, capacity, elementSize, receiver);
    }

    public ChannelProducer<E> producer() {
        return producer;
    }

    public int size() {
        return producer.size();
	}

    public int capacity() {
        return capacity;
    }

    public boolean isEmpty() {
		return size() == 0;
	}

}
