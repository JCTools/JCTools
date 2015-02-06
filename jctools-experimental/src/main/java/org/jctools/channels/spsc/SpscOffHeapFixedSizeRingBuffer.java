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

    private static final Integer MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step",
            4096);
    private static final int READY_MESSAGE_INDICATOR = 0;
    private static final int BUSY_MESSAGE_INDICATOR = 1;
    public static final byte MESSAGE_INDICATOR_SIZE = 4;
    private static final int HEADER_SIZE = 4 * CACHE_LINE_SIZE;

    public static final long EOF = 0;

    private final int lookAheadStep;
    private final ByteBuffer buffy;
    private final long consumerIndexAddress;
    private final long producerIndexAddress;
    private final long producerLookAheadCacheAddress;

    private final long mask;
    private final long bufferAddress;
    private final int messageSize;

    public static int getRequiredBufferSize(final int capacity, final int messageSize) {
        return HEADER_SIZE + (Pow2.roundToPowerOfTwo(capacity) * (messageSize + MESSAGE_INDICATOR_SIZE));
    }

    public static int getLookaheadStep(final int capacity) {
        return Math.min(capacity / 4, MAX_LOOK_AHEAD_STEP);
    }

    public SpscOffHeapFixedSizeRingBuffer(final int capacity, final int messageSize) {
        this(allocateAlignedByteBuffer(getRequiredBufferSize(capacity, messageSize), CACHE_LINE_SIZE), Pow2
                .roundToPowerOfTwo(capacity), true, true, true, messageSize);
    }

    /**
     * This is to be used for an IPC queue with the direct buffer used being a memory mapped file.
     *
     * @param buff
     * @param capacity
     */
    protected SpscOffHeapFixedSizeRingBuffer(final ByteBuffer buff, final int capacity,
            final boolean isProducer, final boolean isConsumer, final boolean initialize,
            final int messageSize) {

        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        this.messageSize = messageSize + MESSAGE_INDICATOR_SIZE;
        this.buffy = alignedSlice(4 * CACHE_LINE_SIZE + (actualCapacity * (this.messageSize)),
                CACHE_LINE_SIZE, buff);

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
        this.mask = actualCapacity - 1;
        // producer owns tail and headCache
        if (isProducer && initialize) {
            spLookAheadCache(0);
            soProducerIndex(0);
            // mark all messages as null
            for (int i = 0; i < actualCapacity; i++) {
                final long offset = offsetForIndex(i);
                readyIndicator(offset);
            }
        }
        // consumer owns head and tailCache
        if (isConsumer && initialize) {
            soConsumerIndex(0);
        }
    }

    /**
     * @return the offset at the next element to be written or EOF if it is not available.
     */
    protected final long writeAcquire() {
        final long producerIndex = lpProducerIndex();
        final long producerLookAhead = lpLookAheadCache();
        final long offsetForIndex = offsetForIndex(producerIndex);
        // verify next lookAheadStep messages are clear to write
        if (producerIndex >= producerLookAhead) {
            final long nextLookAhead = producerIndex + lookAheadStep;
            if (isMessageReady(offsetForIndex(nextLookAhead))) {
                spLookAheadCache(nextLookAhead);
            }
            // OK, can't look ahead, but maybe just next item is ready?
            else if (!isMessageReady(offsetForIndex)) {
                return EOF;
            }
        }
        // return offset for current producer index
        return offsetForIndex;
    }

    protected final void writeRelease(long offset) {
        final long currentProducerIndex = lpProducerIndex();
        busyIndicator(offset);
        soProducerIndex(currentProducerIndex + 1); // StoreStore
    }

    protected final long readAcquire() {
        final long currentHead = lpConsumerIndex();
        final long offset = offsetForIndex(currentHead);
        if (isMessageReady(offset)) {
            return EOF;
        }
        return offset;
    }

    protected final void readRelease(long offset) {
        final long currentHead = lpConsumerIndex();
        readyIndicator(offset);
        soConsumerIndex(currentHead + 1); // StoreStore
    }

    public final int size() {
        return (int) (lvProducerIndex() - lvConsumerIndex());
    }

    public final boolean isEmpty() {
        return lvProducerIndex() == lvConsumerIndex();
    }

    private boolean isMessageReady(long offset) {
        return UNSAFE.getIntVolatile(null, offsetForIndex(offset)) == READY_MESSAGE_INDICATOR;
    }

    private void busyIndicator(long offset) {
        UNSAFE.putOrderedInt(null, offset, BUSY_MESSAGE_INDICATOR);
    }

    private void readyIndicator(long offset) {
        UNSAFE.putOrderedInt(null, offset, READY_MESSAGE_INDICATOR);
    }

    private long offsetForIndex(long currentHead) {
        return bufferAddress + ((currentHead & mask) * messageSize);
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
