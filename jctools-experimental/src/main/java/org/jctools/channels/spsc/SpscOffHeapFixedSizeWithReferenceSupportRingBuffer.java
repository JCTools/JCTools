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

import static org.jctools.util.JvmInfo.CACHE_LINE_SIZE;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeDirectByteBuffer.allocateAlignedByteBuffer;

import java.nio.ByteBuffer;

import org.jctools.channels.OffHeapFixedMessageSizeWithReferenceSupportRingBuffer;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeRefArrayAccess;

public class SpscOffHeapFixedSizeWithReferenceSupportRingBuffer extends OffHeapFixedMessageSizeWithReferenceSupportRingBuffer {

    private static final Integer MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step",
            4096);

    public static final long EOF = 0;

    private final int lookAheadStep;
    private final long producerLookAheadCacheAddress;
    private final WaitStrategy waitStrategy;
    
    public static int getLookaheadStep(final int capacity) {
        return Math.min(capacity / 4, MAX_LOOK_AHEAD_STEP);
    }

    public SpscOffHeapFixedSizeWithReferenceSupportRingBuffer(final int capacity,
            final int messageSize,
            final int arrayMessageSize,
            final WaitStrategy waitStrategy) {
        this(allocateAlignedByteBuffer(getRequiredBufferSize(capacity, messageSize), CACHE_LINE_SIZE),
                Pow2.roundToPowerOfTwo(capacity),
                true,
                true,
                true,
                messageSize,
                createReferenceArray(capacity, arrayMessageSize),
                arrayMessageSize,
                waitStrategy);
    }

    /**
     * This is to be used for an IPC queue with the direct buffer used being a memory mapped file.
     *
     * @param buff
     * @param capacity in messages, actual capacity will be
     * @param messageSize size in bytes for each message
     * @param arrayMessageSize size in element count for each message
     * 
     */
    protected SpscOffHeapFixedSizeWithReferenceSupportRingBuffer(final ByteBuffer buff,
            final int capacity,
            final boolean isProducer,
            final boolean isConsumer,
            final boolean initialize,
            final int messageSize,
            final Object[] references,
            final int arrayMessageSize,
            final WaitStrategy waitStrategy) {
        super(buff, capacity, isProducer, isConsumer, initialize, messageSize, references, arrayMessageSize);
        this.waitStrategy = waitStrategy;

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
    
    public interface WaitStrategy {
        /**
         * This method can implement static or dynamic backoff. Dynamic backoff will rely on the counter for
         * estimating how long the caller has been idling. The expected usage is:
         * 
         * <pre>
         * <code>
         * int ic = 0;
         * while(true) {
         *   if(!isGodotArrived()) {
         *     ic = w.idle(ic);
         *     continue;
         *   }
         *   ic = 0;
         *   // party with Godot until he goes again
         * }
         * </code>
         * </pre>
         * 
         * @param idleCounter idle calls counter, managed by the idle method until reset
         * @return new counter value to be used on subsequent idle cycle
         */
        int idle(int idleCounter);
    }

    protected final long writeAcquireWithWaitStrategy() {
        long wOffset;
        int idleCounter = 0;
        while ((wOffset = this.writeAcquire()) == SpscOffHeapFixedSizeWithReferenceSupportRingBuffer.EOF) {
            idleCounter = waitStrategy.idle(idleCounter);
        }
        return wOffset;
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

    protected final void writeReference(long offset, Object reference) {
        // Is there a way to compute the element offset once and just
        // arithmetic?
        UnsafeRefArrayAccess.spElement(references, UnsafeRefArrayAccess.calcElementOffset(offset), reference);
    }

    protected final Object readReference(long offset) {
        // Is there a way to compute the element offset once and just
        // arithmetic?
        return UnsafeRefArrayAccess.lpElement(references, UnsafeRefArrayAccess.calcElementOffset(offset));
    }
    
    @Override
    protected final void writeRelease(long offset) {
        writeReleaseState(offset);
    }

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
