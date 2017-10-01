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
package org.jctools.channels.mpsc;

import static org.jctools.channels.OffHeapFixedMessageSizeRingBuffer.getRequiredBufferSize;

import java.nio.ByteBuffer;

import org.jctools.channels.Channel;
import org.jctools.channels.ChannelConsumer;
import org.jctools.channels.ChannelProducer;
import org.jctools.channels.ChannelReceiver;
import org.jctools.channels.mapping.Mapper;
import org.jctools.util.Pow2;
import org.jctools.util.Template;

public final class MpscChannel<E> implements Channel<E> {

    // TODO; property configuration
    private static final boolean debugEnabled = false;

    private final int elementSize;
    private final Mapper<E> mapper;
    private final ByteBuffer buffer;
    private final int maximumCapacity;
    private final int requestedCapacity;
    private final MpscChannelProducer<E> producer;

    /**
     * This is to be used for an IPC queue with the direct buffer used being a memory mapped file.
     *
     * @param buffer
     * @param requestedCapacity
     */
    // TODO: take an initialize parameter
    public MpscChannel(final ByteBuffer buffer, final int requestedCapacity, final Class<E> type) {
        this.requestedCapacity = requestedCapacity;
        this.maximumCapacity = getMaximumCapacity(requestedCapacity);
        this.buffer = buffer;
        mapper = new Mapper<E>(type, debugEnabled);
        elementSize = mapper.getSizeInBytes();

        checkSufficientCapacity();
        checkByteBuffer();

        producer = newProducer(type, buffer, maximumCapacity, elementSize);
    }

    private int getMaximumCapacity(int requestedCapacity) {
        return Pow2.roundToPowerOfTwo(requestedCapacity);
    }

    private void checkByteBuffer() {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Channels only work with direct or memory mapped buffers");
        }
    }

    private void checkSufficientCapacity() {
        final int requiredCapacityInBytes = getRequiredBufferSize(maximumCapacity, elementSize);
        if (buffer.capacity() < requiredCapacityInBytes) {
            throw new IllegalArgumentException("Failed to meet required maximumCapacity in bytes: "
                    + requiredCapacityInBytes);
        }
    }

    public ChannelConsumer consumer(ChannelReceiver<E> receiver) {
        return newConsumer(buffer, maximumCapacity, elementSize, receiver);
    }

    public ChannelProducer<E> producer() {
        return producer;
    }

    public int size() {
        return producer.size();
    }

    public int maximumCapacity() {
        return maximumCapacity;
    }

    @Override
    public int requestedCapacity() {
        return requestedCapacity;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    @SuppressWarnings("unchecked")
    private MpscChannelProducer<E> newProducer(final Class<E> type, final Object... args) {
        return mapper.newFlyweight(MpscChannelProducer.class, "ChannelProducerTemplate.java",
                Template.fromFile(Channel.class, "ChannelProducerTemplate.java"), args);
    }

    @SuppressWarnings("unchecked")
    private MpscChannelConsumer<E> newConsumer(Object... args) {
        return mapper.newFlyweight(MpscChannelConsumer.class, "ChannelConsumerTemplate.java",
                Template.fromFile(Channel.class, "ChannelConsumerTemplate.java"), args);
    }

}
