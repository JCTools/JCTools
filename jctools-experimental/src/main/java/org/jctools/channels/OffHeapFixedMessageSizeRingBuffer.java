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
package org.jctools.channels;

import org.jctools.util.JvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeDirectByteBuffer;

import java.nio.ByteBuffer;

import static org.jctools.util.JvmInfo.CACHE_LINE_SIZE;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeDirectByteBuffer.*;

/**
 * Channel protocol:
 * - Fixed message size
 * - 'null' indicator in message preceding byte (potentially use same for type mapping in future)
 * - Use FF algorithm relying on indicator to support in place detection of next element existence
 */
public abstract class OffHeapFixedMessageSizeRingBuffer {

    public static final int READ_RELEASE_INDICATOR = 0;
    public static final int READ_ACQUIRE_INDICATOR = 1;
    public static final int WRITE_RELEASE_INDICATOR = 2;
    public static final int WRITE_ACQUIRE_INDICATOR = 3;
    public static final byte MESSAGE_INDICATOR_SIZE = 4;
    public static final int HEADER_SIZE = 4 * JvmInfo.CACHE_LINE_SIZE;

    public static final long EOF = 0;

    private final ByteBuffer buffy;
    protected final long bufferAddress;
    protected final long consumerIndexAddress;
    protected final long producerIndexAddress;
    protected final long mask;
    protected final int messageSize;

    public static int getRequiredBufferSize(final int capacity, final int messageSize) {
        int alignedMessageSize = (int) Pow2.align(messageSize + MESSAGE_INDICATOR_SIZE, MESSAGE_INDICATOR_SIZE);
        return HEADER_SIZE + (Pow2.roundToPowerOfTwo(capacity) * alignedMessageSize);
    }

    public OffHeapFixedMessageSizeRingBuffer(final int capacity, final int messageSize) {
        this(allocateAlignedByteBuffer(getRequiredBufferSize(capacity, messageSize), CACHE_LINE_SIZE), Pow2
                .roundToPowerOfTwo(capacity), true, true, true, messageSize);
    }

    /**
     * This is to be used for an IPC queue with the direct buffer used being a memory mapped file.
     *
     * @param buff
     * @param capacity in messages, actual capacity will be
     * @param messageSize
     */
    protected OffHeapFixedMessageSizeRingBuffer(final ByteBuffer buff, final int capacity,
            final boolean isProducer, final boolean isConsumer, final boolean initialize,
            final int messageSize) {

        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        // message size is aligned to indicator size, this ensure atomic writes of indicator
        this.messageSize = (int) Pow2.align(messageSize + MESSAGE_INDICATOR_SIZE, MESSAGE_INDICATOR_SIZE);
        this.buffy = alignedSlice(HEADER_SIZE + (actualCapacity * (this.messageSize)), CACHE_LINE_SIZE, buff);

        long alignedAddress = UnsafeDirectByteBuffer.getAddress(buffy);
        if (alignedAddress % JvmInfo.CACHE_LINE_SIZE != 0) {
            throw new IllegalStateException("buffer is expected to be cache line aligned by now");
        }
        // Layout of the RingBuffer (assuming 64b cache line):
        // consumerIndex(8b), pad(56b) |
        // pad(64b) |
        // producerIndex(8b), pad(56b) |
        // pad(64b) |
        // buffer (capacity * messageSize)
        this.consumerIndexAddress = alignedAddress;
        this.producerIndexAddress = this.consumerIndexAddress + 2 * JvmInfo.CACHE_LINE_SIZE;
        this.bufferAddress = alignedAddress + HEADER_SIZE;
        this.mask = actualCapacity - 1;
        // producer owns tail and headCache
        if (isProducer && initialize) {
            soProducerIndex(0);
            // mark all messages as READY
            for (int i = 0; i < actualCapacity; i++) {
                final long offset = offsetForIndex(i);
                readReleaseState(offset);
            }
        }
        // consumer owns head
        if (isConsumer && initialize) {
            soConsumerIndex(0);
        }
    }

    public final int size() {
        return (int) (lvProducerIndex() - lvConsumerIndex());
    }

    public final boolean isEmpty() {
        return lvProducerIndex() == lvConsumerIndex();
    }

    protected final boolean isReadReleased(long offset) {
        return UNSAFE.getIntVolatile(null, offset) == READ_RELEASE_INDICATOR;
    }

    protected final void writeReleaseState(long offset) {
        UNSAFE.putOrderedInt(null, offset, WRITE_RELEASE_INDICATOR);
    }

    protected final void readReleaseState(long offset) {
        UNSAFE.putOrderedInt(null, offset, READ_RELEASE_INDICATOR);
    }

    protected final void writeAcquireState(long offset) {
        UNSAFE.putOrderedInt(null, offset, WRITE_ACQUIRE_INDICATOR);
    }

    protected final void readAcquireState(long offset) {
        UNSAFE.putOrderedInt(null, offset, READ_ACQUIRE_INDICATOR);
    }

    protected final long offsetForIndex(long currentHead) {
        return offsetForIndex(bufferAddress,  mask, messageSize, currentHead);
    }

    protected static long offsetForIndex(long bufferAddress, long mask, int messageSize, long currentHead) {
		return bufferAddress + ((currentHead & mask) * messageSize);
	}

	protected final long lpConsumerIndex() {
        return UNSAFE.getLong(null, consumerIndexAddress);
    }

    protected final long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(null, consumerIndexAddress);
    }

    protected final void soConsumerIndex(final long value) {
        UNSAFE.putOrderedLong(null, consumerIndexAddress, value);
    }

    protected final long lpProducerIndex() {
        return UNSAFE.getLong(null, producerIndexAddress);
    }

    protected final long lvProducerIndex() {
        return UNSAFE.getLongVolatile(null, producerIndexAddress);
    }

    protected final void soProducerIndex(final long value) {
        UNSAFE.putOrderedLong(null, producerIndexAddress, value);
    }

    /**
     * @return a base address for a message acquired to be read, or EOF if none is available
     */
    protected abstract long readAcquire();
    /**
     * @param offset the base address of a message that we are done reading and can be overwritten now
     */
    protected abstract void readRelease(long offset);
    /**
     * @return a base address for a message acquired to be written, or EOF if none is available
     */
    protected abstract long writeAcquire();

    /**
     * @param offset the base address of a message that we are done writing and can be read now
     */
    protected abstract void writeRelease(long offset);

}
