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

import org.jctools.util.JvmInfo;
import org.jctools.util.UnsafeAccess;

import static org.jctools.util.UnsafeAccess.UNSAFE;

public abstract class ConcurrentSequencedCircularArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {
    private static final long ARRAY_BASE;
    private static final int ELEMENT_SHIFT;
    protected static final int SEQ_BUFFER_PAD;
    static {
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(long[].class);
        if (8 == scale) {
            ELEMENT_SHIFT = 3;
        } else {
            throw new IllegalStateException("Unexpected long[] element size");
        }
        // 2 cache lines pad
        SEQ_BUFFER_PAD = (JvmInfo.CACHE_LINE_SIZE * 2) / scale;
        // Including the buffer pad in the array base offset
        ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(long[].class) + (SEQ_BUFFER_PAD * scale);
    }
    protected final long[] sequenceBuffer;

    public ConcurrentSequencedCircularArrayQueue(int capacity) {
        super(capacity);
        int actualCapacity = (int) (this.mask + 1);
        // pad data on either end with some empty slots.
        sequenceBuffer = new long[actualCapacity + SEQ_BUFFER_PAD * 2];
        for (long i = 0; i < actualCapacity; i++) {
            soSequence(sequenceBuffer, calcSequenceOffset(i), i);
        }
    }

    protected final long calcSequenceOffset(long index) {
        return calcSequenceOffset(index, mask);
    }
    protected static long calcSequenceOffset(long index, long mask) {
        return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
    }
    protected final void soSequence(long[] buffer, long offset, long e) {
        UNSAFE.putOrderedLong(buffer, offset, e);
    }

    protected final long lvSequence(long[] buffer, long offset) {
        return UNSAFE.getLongVolatile(buffer, offset);
    }

}
