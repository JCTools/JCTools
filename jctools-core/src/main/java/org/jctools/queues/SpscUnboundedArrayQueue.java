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

public class SpscUnboundedArrayQueue<E> extends BaseSpscLinkedArrayQueue<E> {

    @SuppressWarnings("unchecked")
    public SpscUnboundedArrayQueue(final int chunkSize) {
        int p2capacity = Math.max(Pow2.roundToPowerOfTwo(chunkSize), 16);
        long mask = p2capacity - 1;
        E[] buffer = (E[]) new Object[p2capacity + 1];
        producerBuffer = buffer;
        producerMask = mask;
        producerLookAheadStep = Math.min(p2capacity / 4, SpscArrayQueue.MAX_LOOK_AHEAD_STEP);
        consumerBuffer = buffer;
        consumerMask = mask;
        producerLookAhead = mask - 1; // we know it's all empty to start with
        soProducerIndex(0L);
    }


    protected boolean offerColdPath(final E[] buffer, final long mask, final E e, final long index, final long offset) {
        final int lookAheadStep = producerLookAheadStep;
        // go around the buffer or add a new buffer
        long lookAheadElementOffset = calcElementOffset(index + lookAheadStep, mask);
        if (null == lvElement(buffer, lookAheadElementOffset)) {// LoadLoad
            producerLookAhead = index + lookAheadStep - 1; // joy, there's plenty of room
            writeToQueue(buffer, e, index, offset);
        } else if (null == lvElement(buffer, calcElementOffset(index + 1, mask))) { // buffer is not full
            writeToQueue(buffer, e, index, offset);
        } else {
            linkNewBuffer(buffer, index, offset, e, mask); // add a buffer and link old to new
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void linkNewBuffer(final E[] oldBuffer, final long currIndex, final long offset, final E e,
            final long mask) {
    	// allocate new buffer of same length
        final E[] newBuffer = (E[]) new Object[oldBuffer.length];
        producerBuffer = newBuffer;
        producerLookAhead = currIndex + mask - 1;

        // write to new buffer
        writeToQueue(newBuffer, e, currIndex, offset);
        // link to next buffer and add next indicator as element of old buffer
        soNext(oldBuffer, newBuffer);
        soElement(oldBuffer, offset, JUMP);
    }

}
