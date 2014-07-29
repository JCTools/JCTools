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
import org.jctools.channels.mapping.Flyweight;
import org.jctools.channels.mapping.Mapper;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;
import org.jctools.util.UnsafeDirectByteBuffer;

import java.nio.ByteBuffer;

import static org.jctools.util.UnsafeDirectByteBuffer.CACHE_LINE_SIZE;

public final class SpscChannel<E> implements Channel<E> {

	public static final int SIZE_OF_LONG = 8;
    public static final int HEADER_SIZE = 4 * CACHE_LINE_SIZE;

    // 24b,8b,8b,24b | pad | 24b,8b,8b,24b | pad
    // Reference held to ByteBuffer to stop it from being garbage collected
	private final ByteBuffer buffer;

	private final long headAddress;
	private final long tailCacheAddress;
	private final long tailAddress;
	private final long headCacheAddress;

	private final int capacityInElements;
	private final int mask;
	private final long arrayBase;
    private final int elementSizeInBytes;
    private final Mapper<E> mapper;

    /**
	 * This is to be used for an IPC queue with the direct buffer used being a memory
	 * mapped file.
	 *
	 * @param buffer
	 * @param capacityInElements
	 */
	public SpscChannel(final ByteBuffer buffer, final int capacityInElements, final Class<E> type) {
        mapper = new Mapper<>(type, false);
        elementSizeInBytes = mapper.getSizeInBytes();
        this.capacityInElements = Pow2.roundToPowerOfTwo(capacityInElements);

        checkByteBuffer(buffer);
        checkSufficientCapacity(buffer, this.capacityInElements);

        this.buffer = buffer;

		long alignedAddress = UnsafeDirectByteBuffer.getAddress(buffer);
        headAddress = alignedAddress;
		tailCacheAddress = headAddress + SIZE_OF_LONG;
		tailAddress = headAddress + 2 * CACHE_LINE_SIZE;
		headCacheAddress = tailAddress + SIZE_OF_LONG;
		arrayBase = alignedAddress + HEADER_SIZE;
        mask = this.capacityInElements - 1;
    }

    private void checkByteBuffer(ByteBuffer buff) {
        if (!buff.isDirect()) {
            throw new IllegalArgumentException("Channels only work with direct or memory mapped buffers");
        }
    }

    private void checkSufficientCapacity(ByteBuffer buff, int capacityInElements) {
        final int requiredCapacityInBytes = HEADER_SIZE + elementSizeInBytes * capacityInElements;
        if (buff.capacity() < requiredCapacityInBytes) {
            throw new IllegalArgumentException("Failed to meet required capacity in bytes: " + requiredCapacityInBytes);
        }
    }

    public ChannelConsumer consumer(ChannelReceiver<E> receiver) {
        E element = mapper.newFlyweight(arrayBase);
        return new SpscChannelConsumer<>(element, (Flyweight) element, receiver, this);
    }

    public ChannelProducer<E> producer() {
        E element = mapper.newFlyweight(arrayBase);
        return new SpscChannelProducer<>(element, (Flyweight) element, this);
    }

    public int size() {
        return (int) (getTail() - getHead());
	}

    public int capacity() {
        return capacityInElements;
    }

    public boolean isEmpty() {
		return getTail() == getHead();
	}

    // Package scoped: accessed by other parts of the spsc implementation

    long calcElementOffset(final long index) {
        return arrayBase + ((index & mask) << elementSizeInBytes);
    }

    long getHeadPlain() {
		return UnsafeAccess.UNSAFE.getLong(null, headAddress);
	}

	long getHead() {
		return UnsafeAccess.UNSAFE.getLongVolatile(null, headAddress);
	}

	void setHead(final long value) {
		UnsafeAccess.UNSAFE.putOrderedLong(null, headAddress, value);
	}

	long getTailPlain() {
		return UnsafeAccess.UNSAFE.getLong(null, tailAddress);
	}

	long getTail() {
		return UnsafeAccess.UNSAFE.getLongVolatile(null, tailAddress);
	}

	void setTail(final long value) {
		UnsafeAccess.UNSAFE.putOrderedLong(null, tailAddress, value);
	}

	long getHeadCache() {
		return UnsafeAccess.UNSAFE.getLong(null, headCacheAddress);
	}

	void setHeadCache(final long value) {
		UnsafeAccess.UNSAFE.putLong(headCacheAddress, value);
	}

	long getTailCache() {
		return UnsafeAccess.UNSAFE.getLong(null, tailCacheAddress);
	}

	void setTailCache(final long value) {
		UnsafeAccess.UNSAFE.putLong(tailCacheAddress, value);
	}

}
