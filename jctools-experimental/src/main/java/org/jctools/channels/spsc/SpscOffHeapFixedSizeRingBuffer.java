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

import org.jctools.util.Pow2;
import org.jctools.util.UnsafeDirectByteBuffer;

import java.nio.ByteBuffer;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeDirectByteBuffer.*;

/**
 * Channel protocol:
 * - Fixed message size
 * - 'null' indicator in message preceding byte (potentially use same for type mapping in future)
 * - Use FF algorithm relying on indicator to support in place detection of next element existence
 */
public class SpscOffHeapFixedSizeRingBuffer {

    private static final Integer MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step", 4096);
    private static final byte NULL_MESSAGE_INDICATOR = 0;
    private static final byte WRITTEN_MESSAGE_INDICATOR = 1;
    private static final byte MESSAGE_INDICATOR_SIZE = 1;
    private static final int HEADER_SIZE = 4 * CACHE_LINE_SIZE;

    public static final long EOF = 0;

    protected final int lookAheadStep;
    private final ByteBuffer buffy;
    private final long consumerIndexAddress;
    private final long producerIndexAddress;
    private final long producerLookAheadCacheAddress;

    private final int capacity;
    private final int mask;
    private final long bufferAddress;
    private final int messageSize;

    public static int getRequiredBufferSize(final int capacity, final int messageSize) {
        return HEADER_SIZE + (Pow2.roundToPowerOfTwo(capacity) * (messageSize + MESSAGE_INDICATOR_SIZE));
    }


    public static int getLookaheadStep(final int capacity) {
        return Math.min(capacity / 4, MAX_LOOK_AHEAD_STEP);
    }

    public SpscOffHeapFixedSizeRingBuffer(final int capacity, final int messageSize) {
        this(allocateAlignedByteBuffer(
                        getRequiredBufferSize(capacity, messageSize),
                        CACHE_LINE_SIZE),
                Pow2.roundToPowerOfTwo(capacity), true, true, true, messageSize);
    }

    /**
     * This is to be used for an IPC queue with the direct buffer used being a memory
     * mapped file.
     *
     * @param buff
     * @param capacity
     */
    protected SpscOffHeapFixedSizeRingBuffer(
            final ByteBuffer buff,
            final int capacity,
            final boolean isProducer,
            final boolean isConsumer,
            final boolean initialize,
            final int messageSize) {

        this.capacity = Pow2.roundToPowerOfTwo(capacity);
        this.messageSize = messageSize + MESSAGE_INDICATOR_SIZE;
        this.buffy = alignedSlice(4 * CACHE_LINE_SIZE + (this.capacity * (this.messageSize)), CACHE_LINE_SIZE, buff);

        long alignedAddress = UnsafeDirectByteBuffer.getAddress(buffy);
        this.lookAheadStep = getLookaheadStep(capacity);
        // Layout of the RingBuffer (assuming 64b cache line):
        // consumerIndex(8b), pad(56b) |
        // pad(64b) |
        // producerIndex(8b), producerLookAheadCache(8b), pad(48b) |
        // pad(64b) |
        // buffer (capacity * messageSize)
        this.consumerIndexAddress = alignedAddress;
        this.producerIndexAddress = this.consumerIndexAddress + 2 * CACHE_LINE_SIZE;
        this.producerLookAheadCacheAddress = this.producerIndexAddress + 8;
        this.bufferAddress = alignedAddress + HEADER_SIZE;
        this.mask = this.capacity - 1;
        // producer owns tail and headCache
        if (isProducer && initialize) {
            spLookAheadCache(0);
            soProducerIndex(0);
            // mark all messages as null
            for (int i = 0; i < this.capacity; i++) {
                final long offset = offsetForIndex(i);
                nullIdicator(offset);
            }
        }
        // consumer owns head and tailCache
        if (isConsumer && initialize) {
            soConsumerIndex(0);
        }
    }


	private void nullIdicator(final long offset) {
		UNSAFE.putByte(offset, NULL_MESSAGE_INDICATOR);
	}

    /**
     * NOTE: for this implementation the allocated capacity may not be used to it's fullest as the producer may stop
     * writing to the queue up to lookAheadStep elements before full capacity. The actual capacity therefore is between
     * <i>(capacity - lookAheadStep)</i> and <i>capacity</i>.
     * @return the offset at the next element to be written or EOF if it is not available.
     */
    protected final long writeAcquire() {
        final long producerIndex = lpProducerIndex();
        final long producerLookAhead = lpLookAheadCache();
        // verify next lookAheadStep messages are clear to write
        if (producerIndex >= producerLookAhead) {
            final long nextLookAhead = producerIndex + lookAheadStep;
            if (UNSAFE.getByteVolatile(null, offsetForIndex(nextLookAhead)) != NULL_MESSAGE_INDICATOR) {
                return EOF;
            }
            spLookAheadCache(nextLookAhead);
        }
        // return offset for current producer index
        return offsetForIndex(producerIndex);
    }

    protected final void writeRelease(long offset) {
        final long currentProducerIndex = lpProducerIndex();
        // ideally we would have used the byte write as a barrier, but there's no ordered write for byte
        // we could consider using an integer indicator instead of a byte which would also improve likelihood
        // of aligned writes.
        writeIndicator(offset);
        soProducerIndex(currentProducerIndex + 1); // StoreStore
    }


	private void writeIndicator(long offset) {
		UNSAFE.putByte(offset, WRITTEN_MESSAGE_INDICATOR);
	}

    protected final long readAcquire() {
        final long currentHead = lpConsumerIndex();
        final long offset = offsetForIndex(currentHead);
        if (UNSAFE.getByteVolatile(null, offset) == NULL_MESSAGE_INDICATOR) {
            return EOF;
        }
        return offset;
    }

    protected final void readRelease(long offset) {
        final long currentHead = lpConsumerIndex();
        nullIdicator(offset);
        soConsumerIndex(currentHead + 1); // StoreStore
    }

    private long offsetForIndex(final long currentHead) {
        return bufferAddress + ((currentHead & mask) * messageSize);
    }

    public final int size() {
        return (int) (lvProducerIndex() - lvConsumerIndex());
    }

    public final boolean isEmpty() {
        return lvProducerIndex() == lvConsumerIndex();
    }

    private long lpConsumerIndex() {
        return UNSAFE.getLong(null, consumerIndexAddress);
    }

    private long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(null, consumerIndexAddress);
    }

    private void soConsumerIndex(final long value) {
        UNSAFE.putOrderedLong(null, consumerIndexAddress, value);
    }

    private long lpProducerIndex() {
        return UNSAFE.getLong(null, producerIndexAddress);
    }

    private long lvProducerIndex() {
        return UNSAFE.getLongVolatile(null, producerIndexAddress);
    }

    private void soProducerIndex(final long value) {
        UNSAFE.putOrderedLong(null, producerIndexAddress, value);
    }

    private long lpLookAheadCache() {
        return UNSAFE.getLong(null, producerLookAheadCacheAddress);
    }

    private void spLookAheadCache(final long value) {
        UNSAFE.putLong(producerLookAheadCacheAddress, value);
    }
}
