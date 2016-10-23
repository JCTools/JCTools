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
package org.jctools.queues;

import static org.jctools.queues.CircularArrayOffsetCalculator.allocate;
import static org.jctools.queues.CircularArrayOffsetCalculator.calcElementOffset;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;

import org.jctools.util.Pow2;

public class SpscChunkedArrayQueue<E> extends BaseSpscLinkedArrayQueue<E> {
    private int maxQueueCapacity;
    private long producerQueueLimit;

    public SpscChunkedArrayQueue(final int capacity) {
        this(Math.max(8, Pow2.roundToPowerOfTwo(capacity / 8)), capacity);
    }

    public SpscChunkedArrayQueue(final int chunkSize, final int capacity) {
        if (capacity < 16) {
            throw new IllegalArgumentException("Max capacity must be 4 or more");
        }
        // minimal chunk size of eight makes sure minimal lookahead step is 2
        if (chunkSize < 8) {
            throw new IllegalArgumentException("Chunk size must be 2 or more");
        }

        maxQueueCapacity = Pow2.roundToPowerOfTwo(capacity);
        int chunkCapacity = Pow2.roundToPowerOfTwo(chunkSize);
        if (chunkCapacity >= maxQueueCapacity) {
            throw new IllegalArgumentException(
                    "Initial capacity cannot exceed maximum capacity(both rounded up to a power of 2)");
        }

        long mask = chunkCapacity - 1;
        // need extra element to point at next array
        E[] buffer = allocate(chunkCapacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        producerBufferLimit = mask - 1; // we know it's all empty to start with
        producerQueueLimit = maxQueueCapacity;
        soProducerIndex(0L);// serves as a StoreStore barrier to support correct publication
    }

    @Override
    protected final boolean offerColdPath(E[] buffer, long mask, E e, long pIndex, long offset) {
        // use a fixed lookahead step based on buffer capacity
        final long lookAheadStep = (mask + 1) / 4;
        long pBufferLimit = pIndex + lookAheadStep;

        long pQueueLimit = producerQueueLimit;

        if (pIndex >= pQueueLimit) {
            // we tested against a potentially out of date queue limit, refresh it
            long cIndex = lvConsumerIndex();
            producerQueueLimit = pQueueLimit = cIndex + maxQueueCapacity;
            // if we're full we're full
            if (pIndex >= pQueueLimit) {
                return false;
            }
        }
        // if buffer limit is after queue limit we use queue limit. We need to handle overflow so
        // cannot use Math.min
        if (pBufferLimit - pQueueLimit > 0) {
            pBufferLimit = pQueueLimit;
        }

        // go around the buffer or add a new buffer
        if (pBufferLimit > pIndex + 1 && // there's sufficient room in buffer/queue to use pBufferLimit
                null == lvElement(buffer, calcElementOffset(pBufferLimit, mask))) {
            producerBufferLimit = pBufferLimit - 1; // joy, there's plenty of room
            writeToQueue(buffer, e, pIndex, offset);
        }
        else if (null == lvElement(buffer, calcElementOffset(pIndex + 1, mask))) { // buffer is not full
            writeToQueue(buffer, e, pIndex, offset);
        }
        else {
            // we got one slot left to write into, and we are not full. Need to link new buffer.
            // allocate new buffer of same length
            final E[] newBuffer = allocate((int)(mask + 2));
            producerBuffer = newBuffer;

            linkOldToNew(pIndex, buffer, offset, newBuffer, offset, e);
        }
        return true;
    }

}
