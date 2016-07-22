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

public class SpscGrowableArrayQueue<E> extends SpscChunkedArrayQueue<E> {
    public SpscGrowableArrayQueue(final int capacity) {
        super(capacity);
    }

    public SpscGrowableArrayQueue(final int initialCapacity, int capacity) {
        super(initialCapacity, capacity);
    }

    @Override
    protected void linkNewBuffer(final E[] oldBuffer, final long currIndex, final long offset, final E e,
            final long mask) {
        // allocate new buffer of same length
        final E[] newBuffer = allocate((int) (2*(mask+1) + 1));

        producerBuffer = newBuffer;
        producerMask = newBuffer.length - 2;

        final long offsetInNew = calcElementOffset(currIndex, producerMask);
        linkOldToNew(currIndex, oldBuffer, offset, newBuffer, offsetInNew, e);
    }
}
