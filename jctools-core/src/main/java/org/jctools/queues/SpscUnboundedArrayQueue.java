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

import org.jctools.util.Pow2;

public class SpscUnboundedArrayQueue<E> extends BaseSpscLinkedArrayQueue<E> {

    @SuppressWarnings("unchecked")
    public SpscUnboundedArrayQueue(final int chunkSize) {
        int p2capacity = Math.max(Pow2.roundToPowerOfTwo(chunkSize), 16);
        long mask = p2capacity - 1;
        E[] buffer = (E[]) new Object[p2capacity + 1];
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        producerBufferLimit = mask - 1; // we know it's all empty to start with
        soProducerIndex(0L);
    }


    @Override
    @SuppressWarnings("unchecked")
    protected void linkNewBuffer(final E[] oldBuffer, final long currIndex, final long offset, final E e,
            final long mask) {
    	// allocate new buffer of same length
        final E[] newBuffer = (E[]) new Object[oldBuffer.length];
        producerBuffer = newBuffer;
        producerBufferLimit = currIndex + mask - 1;

        linkOldToNew(currIndex, oldBuffer, offset, newBuffer, offset, e);
    }

    @Override
    protected final boolean isBounded() {
        return false;
    }
}
