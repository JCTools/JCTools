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

public class SpscUnboundedArrayQueue<E> extends BaseSpscLinkedArrayQueue<E> {

    public SpscUnboundedArrayQueue(final int chunkSize) {
        int chunkCapacity = Math.max(Pow2.roundToPowerOfTwo(chunkSize), 16);
        long mask = chunkCapacity - 1;
        E[] buffer = allocate(chunkCapacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        producerBufferLimit = mask - 1; // we know it's all empty to start with
        soProducerIndex(0L);
    }

    @Override
    protected boolean offerColdPath(E[] buffer, long mask, E e, long pIndex, long offset) {
        // use a fixed lookahead step based on buffer capacity
        final long lookAheadStep = (mask + 1) / 4;
        long pBufferLimit = pIndex + lookAheadStep;

        // go around the buffer or add a new buffer
        if (null == lvElement(buffer, calcElementOffset(pBufferLimit, mask))) {
            producerBufferLimit = pBufferLimit - 1; // joy, there's plenty of room
            writeToQueue(buffer, e, pIndex, offset);
        }
        else if (null == lvElement(buffer, calcElementOffset(pIndex + 1, mask))) { // buffer is not full
            writeToQueue(buffer, e, pIndex, offset);
        }
        else {
            // we got one slot left to write into, and we are not full. Need to link new buffer.
            // allocate new buffer of same length
            final E[] newBuffer =  allocate((int)(mask + 2));
            producerBuffer = newBuffer;
            producerBufferLimit = pIndex + mask - 1;

            linkOldToNew(pIndex, buffer, offset, newBuffer, offset, e);
        }
        return true;
    }

}
