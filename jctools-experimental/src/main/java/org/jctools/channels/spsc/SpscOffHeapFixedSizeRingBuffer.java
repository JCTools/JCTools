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

import static org.jctools.util.PortableJvmInfo.CACHE_LINE_SIZE;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeDirectByteBuffer.allocateAlignedByteBuffer;

import java.nio.ByteBuffer;

import org.jctools.channels.OffHeapFixedMessageSizeRingBuffer;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeRefArrayAccess;

/**
 * Channel protocol:
 * - Fixed message size
 * - 'null' indicator in message preceding byte (potentially use same for type mapping in future)
 * - Use FF algorithm relying on indicator to support in place detection of next element existence
 */
public class SpscOffHeapFixedSizeRingBuffer extends OffHeapFixedMessageSizeRingBuffer {

    private static final Integer MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step",
            4096);

    public static final long EOF = 0;

    private final int lookAheadStep;
    private final long producerLookAheadCacheAddress;

    public static int getLookaheadStep(final int capacity) {
        return Math.min(capacity / 4, MAX_LOOK_AHEAD_STEP);
    }

    public SpscOffHeapFixedSizeRingBuffer(final int capacity, final int messageSize, final int referenceMessageSize) {
        this(allocateAlignedByteBuffer(getRequiredBufferSize(capacity, messageSize), CACHE_LINE_SIZE),
                Pow2.roundToPowerOfTwo(capacity),
                true,
                true,
                true,
                messageSize,
                createReferenceArray(capacity, referenceMessageSize),
                referenceMessageSize);
    }

    /**
     * This is to be used for an IPC queue with the direct buffer used being a memory mapped file.
     *
     * @param buff
     * @param capacity in messages, actual capacity will be
     * @param messageSize
     */
    protected SpscOffHeapFixedSizeRingBuffer(final ByteBuffer buff,
            final int capacity,
            final boolean isProducer,
            final boolean isConsumer,
            final boolean initialize,
            final int messageSize,
            final Object[] references,
            final int referenceMessageSize) {
        super(buff,
                capacity,
                isProducer,
                isConsumer,
                initialize,
                messageSize,
                references,
                referenceMessageSize);

        this.lookAheadStep = getLookaheadStep(capacity);
        // Layout of the RingBuffer (assuming 64b cache line):
        // consumerIndex(8b), pad(56b) |
        // pad(64b) |
        // producerIndex(8b), producerLookAheadCache(8b), pad(48b) |
        // pad(64b) |
        // buffer (capacity * messageSize)
        this.producerLookAheadCacheAddress = this.producerIndexAddress + 8;

        // producer owns tail and headCache
        if (isProducer && initialize) {
            spLookAheadCache(0);
        }
    }

    @Override
    protected final long writeAcquire() {
        final long producerIndex = lpProducerIndex();
        final long producerLookAhead = lpLookAheadCache();
        final long producerOffset = offsetForIndex(bufferAddress, mask, messageSize, producerIndex);
        // verify next lookAheadStep messages are clear to write
        if (producerIndex >= producerLookAhead) {
            final long nextLookAhead = producerIndex + lookAheadStep;
            if (isReadReleased(offsetForIndex(nextLookAhead))) {
                spLookAheadCache(nextLookAhead);
            }
            // OK, can't look ahead, but maybe just next item is ready?
            else if (!isReadReleased(producerOffset)) {
                return EOF;
            }
        }
        soProducerIndex(producerIndex + 1); // StoreStore
//        writeAcquireState(producerOffset);
        // return offset for current producer index
        return producerOffset;
    }

    @Override
    protected final void writeRelease(long offset) {
        writeReleaseState(offset);
    }

    @Override
    protected final void writeRelease(long offset, int type) {
        assert type != 0;
        UNSAFE.putOrderedInt(null, offset, type);
    }

    @Override
    protected final long readAcquire() {
        final long consumerIndex = lpConsumerIndex();
        final long consumerOffset = offsetForIndex(consumerIndex);
        if (isReadReleased(consumerOffset)) {
            return EOF;
        }
        soConsumerIndex(consumerIndex + 1); // StoreStore
//        readAcquireState(consumerOffset);
        return consumerOffset;
    }

    @Override
    protected final void readRelease(long offset) {
        readReleaseState(offset);

    }

    private long lpLookAheadCache() {
        return UNSAFE.getLong(null, producerLookAheadCacheAddress);
    }

    private void spLookAheadCache(final long value) {
        UNSAFE.putLong(producerLookAheadCacheAddress, value);
    }
}
