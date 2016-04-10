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

import static org.jctools.util.UnsafeAccess.UNSAFE;

import org.jctools.util.Pow2;

import static org.jctools.util.UnsafeRefArrayAccess.REF_ARRAY_BASE;
import static org.jctools.util.UnsafeRefArrayAccess.REF_ELEMENT_SHIFT;

public class SpscUnboundedArrayQueue<E> extends BaseSpscLinkedArrayQueue<E> {
    private static final int MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step", 4096);

    @SuppressWarnings("unchecked")
    public SpscUnboundedArrayQueue(final int chunkSize) {
        int p2capacity = Math.max(Pow2.roundToPowerOfTwo(chunkSize), 16);
        long mask = p2capacity - 1;
        E[] buffer = (E[]) new Object[p2capacity + 1];
        producerBuffer = buffer;
        producerMask = mask;
        producerLookAheadStep = Math.min(p2capacity / 4, MAX_LOOK_AHEAD_STEP);
        consumerBuffer = buffer;
        consumerMask = mask;
        producerLookAhead = mask - 1; // we know it's all empty to start with
        soProducerIndex(0L);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public final boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = producerBuffer;
        final long index = producerIndex;
        final long mask = producerMask;
        final long offset = calcWrappedOffset(index, mask);
        if (index < producerLookAhead) {
            writeToQueue(buffer, e, index, offset);
        } else {
            final int lookAheadStep = producerLookAheadStep;
            // go around the buffer or add a new buffer
            long lookAheadElementOffset = calcWrappedOffset(index + lookAheadStep, mask);
            if (null == lvElement(buffer, lookAheadElementOffset)) {// LoadLoad
                producerLookAhead = index + lookAheadStep - 1; // joy, there's plenty of room
                writeToQueue(buffer, e, index, offset);
            } else if (null == lvElement(buffer, calcWrappedOffset(index + 1, mask))) { // buffer is not full
                writeToQueue(buffer, e, index, offset);
            } else {
                linkNewBuffer(buffer, index, offset, e, mask); // add a buffer and link old to new
            }
        }
        return true;
    }

    private void writeToQueue(final E[] buffer, final E e, final long index, final long offset) {
        soProducerIndex(index + 1);// this ensures atomic write of long on 32bit platforms
        soElement(buffer, offset, e);// StoreStore
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
        soElement(oldBuffer, offset, JUMP); // new buffer is visible after element is inserted
    }

    private void soNext(E[] curr, E[] next) {
        soElement(curr, calcDirectOffset(curr.length -1), next);
    }
    @SuppressWarnings("unchecked")
	private E[] lvNext(E[] curr) {
        final long nextArrayOffset = calcDirectOffset(curr.length -1);
        final E[] nextBuffer = (E[]) lvElement(curr, nextArrayOffset);
        soElement(curr, nextArrayOffset, null);
        return nextBuffer;
    }
    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public final E poll() {
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long mask = consumerMask;
        final long offset = calcWrappedOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        boolean isNextBuffer = e == JUMP;
        if (null != e && !isNextBuffer) {
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soElement(buffer, offset, null);// StoreStore
            return (E) e;
        } else if (isNextBuffer) {
            return newBufferPoll(buffer, index, mask);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private E newBufferPoll(E[] buffer, final long index, final long mask) {
        E[] nextBuffer = lvNext(buffer);
        consumerBuffer = nextBuffer;
        final long offsetInNew = calcWrappedOffset(index, mask);
        final E n = (E) lvElement(nextBuffer, offsetInNew);// LoadLoad
        soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
        soElement(nextBuffer, offsetInNew, null);// StoreStore
        // prevent extended retention if the buffer is in old gen and the nextBuffer is in young gen
        soNext(buffer, null);
        return n;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public final E peek() {
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long mask = consumerMask;
        final long offset = calcWrappedOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        if (e == JUMP) {
            return newBufferPeek(lvNext(buffer), index, mask);
        }

        return (E) e;
    }

    @SuppressWarnings("unchecked")
    private E newBufferPeek(E[] nextBuffer, final long index, final long mask) {
        consumerBuffer = nextBuffer;
        final long offsetInNew = calcWrappedOffset(index, mask);
        return (E) lvElement(nextBuffer, offsetInNew);// LoadLoad
    }

    private static long calcWrappedOffset(long index, long mask) {
        return calcDirectOffset(index & mask);
    }

    private static long calcDirectOffset(long index) {
        return REF_ARRAY_BASE + (index << REF_ELEMENT_SHIFT);
    }
    private static void soElement(Object[] buffer, long offset, Object e) {
        UNSAFE.putOrderedObject(buffer, offset, e);
    }
    private static <E> Object lvElement(E[] buffer, long offset) {
        return UNSAFE.getObjectVolatile(buffer, offset);
    }
}
