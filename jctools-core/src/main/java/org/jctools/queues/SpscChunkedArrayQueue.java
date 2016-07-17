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

import static org.jctools.queues.CircularArrayOffsetCalculator.calcElementOffset;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

import org.jctools.util.Pow2;

public class SpscChunkedArrayQueue<E> extends BaseSpscLinkedArrayQueue<E> {

    @SuppressWarnings("unchecked")
    public SpscChunkedArrayQueue(final int chunkSize, final int capacity) {
        maxQueueCapacity = Math.max(Pow2.roundToPowerOfTwo(capacity), 64);
        int p2capacity = Math.max(Pow2.roundToPowerOfTwo(chunkSize), 16);
        long mask = p2capacity - 1;
        E[] buffer = (E[]) new Object[p2capacity + 1];

        producerBuffer = buffer;
        producerMask = mask;
        producerBufferLimit = mask - 1; // we know it's all empty to start with
        producerQueueLimit = maxQueueCapacity;

        consumerBuffer = buffer;
        consumerMask = mask;

        soProducerIndex(0L);
    }

    protected boolean offerColdPath(final E[] buffer, final long mask, final E e, final long pIndex,
            final long offset) {

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

        // use a fixed lookahead step based on buffer capacity
        final long lookAheadStep = (mask + 1) >> 2;

        // go around the buffer or add a new buffer
        final long pBufferLimit = Math.min(pIndex + lookAheadStep, pQueueLimit);

        if (pBufferLimit > pIndex + 1 && // there's sufficient room in buffer/queue to use pBufferLimit
            null == lvElement(buffer, calcElementOffset(pBufferLimit, mask)))
        {
            producerBufferLimit = pBufferLimit - 1; // joy, there's plenty of room
            writeToQueue(buffer, e, pIndex, offset);
        } else if (null == lvElement(buffer, calcElementOffset(pIndex + 1, mask))) { // buffer is not full
            writeToQueue(buffer, e, pIndex, offset);
        }
        else {
            // we got one slot left to write into, and we are not full. Need to link new buffer.
            linkNewBuffer(buffer, pIndex, offset, e, mask);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void linkNewBuffer(final E[] oldBuffer, final long currIndex, final long offset, final E e,
            final long mask) {
        // allocate new buffer of same length
        final E[] newBuffer = (E[]) new Object[oldBuffer.length];
        producerBuffer = newBuffer;

        // write to new buffer
        soElement(newBuffer, offset, e);// StoreStore
        // link to next buffer and add next indicator as element of old buffer
        soNext(oldBuffer, newBuffer);
        soElement(oldBuffer, offset, JUMP);
        // index is visible after elements (isEmpty/poll ordering)
        soProducerIndex(currIndex + 1);// this ensures atomic write of long on 32bit platforms
    }

}
