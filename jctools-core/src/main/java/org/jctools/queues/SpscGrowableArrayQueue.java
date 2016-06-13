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

import static java.lang.Math.max;
import static org.jctools.queues.CircularArrayOffsetCalculator.allocate;
import static org.jctools.queues.CircularArrayOffsetCalculator.calcElementOffset;
import static org.jctools.util.Pow2.roundToPowerOfTwo;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

public class SpscGrowableArrayQueue<E> extends BaseSpscLinkedArrayQueue<E> {
    public SpscGrowableArrayQueue(final int capacity) {
        this(roundToPowerOfTwo(max(capacity, 32) / 2), max(capacity, 32));
    }

    public SpscGrowableArrayQueue(final int initialCapacity, int capacity) {
        int p2initialCapacity = roundToPowerOfTwo(max(initialCapacity, 32)/2);
        int p2capacity = roundToPowerOfTwo(max(capacity, 32));
        if (p2initialCapacity >= p2capacity) {
            throw new IllegalArgumentException("Initial capacity("+initialCapacity+") rounded up to a power of 2 cannot exceed maximum capacity ("+capacity+")rounded up to a power of 2");
        }

        long mask = p2initialCapacity - 1;
        // need extra element to point at next array
        E[] buffer = allocate(p2initialCapacity+1);
        producerBuffer = buffer;
        producerMask = mask;
        adjustLookAheadStep(p2initialCapacity);
        consumerBuffer = buffer;
        consumerMask = mask;
        maxQueueCapacity = p2capacity;
        producerLimit = mask - 1; // we know it's all empty to start with
        soProducerIndex(0L);// serves as a StoreStore barrier to support correct publication
    }


    protected boolean offerColdPath(final E[] buffer, final long mask, final E e, final long index,
            final long offset) {
        final int lookAheadStep = producerLookAheadStep;
        // normal case, go around the buffer or resize if full (unless we hit max capacity)
        if (lookAheadStep > 0) {
            long lookAheadElementOffset = calcElementOffset(index + lookAheadStep, mask);
            // Try and look ahead a number of elements so we don't have to do this all the time
            if (null == lvElement(buffer, lookAheadElementOffset)) {
                producerLimit = index + lookAheadStep - 1; // joy, there's plenty of room
                writeToQueue(buffer, e, index, offset);
                return true;
            }
            // we're at max capacity, can use up last element
            final int maxCapacity = maxQueueCapacity;
            if (mask + 1 == maxCapacity) {
                if (null == lvElement(buffer, offset)) {
                    writeToQueue(buffer, e, index, offset);
                    return true;
                }
                // we're full and can't grow
                return false;
            }
            // not at max capacity, so must allow extra slot for next buffer pointer
            if (null == lvElement(buffer, calcElementOffset(index + 1, mask))) { // buffer is not full
                writeToQueue(buffer, e, index, offset);
            } else {
                final int newCapacity = (int) (2 * (mask + 1));
                final E[] newBuffer = allocate(newCapacity + 1);
                producerBuffer = newBuffer;
                producerMask = (long) (newCapacity - 1);
                if (newCapacity == maxCapacity) {
                    long currConsumerIndex = lvConsumerIndex();
                    // use lookAheadStep to store the consumer distance from final buffer
                    producerLookAheadStep = -(int) (index - currConsumerIndex);
                    producerLimit = currConsumerIndex + maxCapacity - 1;
                } else {
                    producerLimit = index + producerMask - 1;
                    adjustLookAheadStep(newCapacity);
                }
                final long offsetInNew = calcElementOffset(index, producerMask);
                soElement(newBuffer, offsetInNew, e);// StoreStore
                soNext(buffer, newBuffer); // new buffer is visible after element is inserted
                soElement(buffer, offset, JUMP); // new buffer is visible after element is inserted
                // index is visible after elements (isEmpty/poll ordering)
                soProducerIndex(index + 1);// this ensures correctness on 32bit platforms
            }
            return true;
        }
        // the step is negative (or zero) in the period between allocating the max sized buffer and the
        // consumer starting on it
        else {
            final int prevElementsInOtherBuffers = -lookAheadStep;
            // until the consumer starts using the current buffer we need to check consumer index to
            // verify size
            long currConsumerIndex = lvConsumerIndex();
            int size = (int) (index - currConsumerIndex);
            int maxCapacity = (int) mask+1; // we're on max capacity or we wouldn't be here
            if (size == maxCapacity) {
                // consumer index has not changed since adjusting the lookAhead index, we're full
                return false;
            }
            // if consumerIndex progressed enough so that current size indicates it is on same buffer
            long firstIndexInCurrentBuffer = producerLimit - maxCapacity + prevElementsInOtherBuffers;
            if (currConsumerIndex >= firstIndexInCurrentBuffer) {
                // job done, we've now settled into our final state
                adjustLookAheadStep(maxCapacity);
            }
            // consumer is still on some other buffer
            else {
                // how many elements out of buffer?
                producerLookAheadStep = (int) (currConsumerIndex - firstIndexInCurrentBuffer);
            }
            producerLimit = currConsumerIndex + maxCapacity;
            writeToQueue(buffer, e, index, offset);
            return true;
        }
    }


    private void adjustLookAheadStep(int capacity) {
        producerLookAheadStep = Math.min(capacity / 4, SpscArrayQueue.MAX_LOOK_AHEAD_STEP);
    }
}
