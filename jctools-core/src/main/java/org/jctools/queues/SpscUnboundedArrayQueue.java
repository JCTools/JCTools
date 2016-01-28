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

import java.lang.reflect.Field;
import java.util.AbstractQueue;
import java.util.Iterator;

import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

abstract class SpscUnboundedArrayQueuePrePad<E> extends AbstractQueue<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12;
    // p13, p14, p15, p16, p17; drop 4 longs, the cold fields act as buffer
}
abstract class SpscUnboundedArrayQueueProducerColdFields<E> extends SpscUnboundedArrayQueuePrePad<E> {
    protected int producerLookAheadStep;
    protected long producerLookAhead;
    protected long producerMask;
    protected E[] producerBuffer;
}
abstract class SpscUnboundedArrayQueueProducerFields<E> extends SpscUnboundedArrayQueueProducerColdFields<E> {
    protected long producerIndex;
}

abstract class SpscUnboundedArrayQueueL2Pad<E> extends SpscUnboundedArrayQueueProducerFields<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}
abstract class SpscUnboundedArrayQueueConsumerColdField<E> extends SpscUnboundedArrayQueueL2Pad<E> {
    protected long consumerMask;
    protected E[] consumerBuffer;
}

abstract class SpscUnboundedArrayQueueConsumerField<E> extends SpscUnboundedArrayQueueConsumerColdField<E> {
    protected long consumerIndex;
}

public class SpscUnboundedArrayQueue<E> extends SpscUnboundedArrayQueueConsumerField<E>
    implements QueueProgressIndicators{
    static final int MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step", 4096);
    private final static long P_INDEX_OFFSET;
    private final static long C_INDEX_OFFSET;
    private static final long REF_ARRAY_BASE;
    private static final int REF_ELEMENT_SHIFT;
    private static final Object HAS_NEXT = new Object();
    static {
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale) {
            REF_ELEMENT_SHIFT = 2;
        } else if (8 == scale) {
            REF_ELEMENT_SHIFT = 3;
        } else {
            throw new IllegalStateException("Unknown pointer size");
        }
        // Including the buffer pad in the array base offset
        REF_ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class);
        try {
            Field iField = SpscUnboundedArrayQueueProducerFields.class.getDeclaredField("producerIndex");
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = SpscUnboundedArrayQueueConsumerField.class.getDeclaredField("consumerIndex");
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

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

    @Override
    public final Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public final boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
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
        soElement(oldBuffer, offset, HAS_NEXT); // new buffer is visible after element is inserted
    }

    private void soNext(E[] curr, E[] next) {
        soElement(curr, calcDirectOffset(curr.length -1), next);
    }
    @SuppressWarnings("unchecked")
	private E[] lvNext(E[] curr) {
        return (E[]) lvElement(curr, calcDirectOffset(curr.length -1));
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
        boolean isNextBuffer = e == HAS_NEXT;
        if (null != e && !isNextBuffer) {
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soElement(buffer, offset, null);// StoreStore
            return (E) e;
        } else if (isNextBuffer) {
            return newBufferPoll(lvNext(buffer), index, mask);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private E newBufferPoll(E[] nextBuffer, final long index, final long mask) {
        consumerBuffer = nextBuffer;
        final long offsetInNew = calcWrappedOffset(index, mask);
        final E n = (E) lvElement(nextBuffer, offsetInNew);// LoadLoad
        if (null == n) {
            return null;
        } else {
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soElement(nextBuffer, offsetInNew, null);// StoreStore
            return n;
        }
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
        if (e == HAS_NEXT) {
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

    @Override
    public final int size() {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        while (true) {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after) {
                return (int) (currentProducerIndex - after);
            }
        }
    }

    private void adjustLookAheadStep(int capacity) {
        producerLookAheadStep = Math.min(capacity / 4, MAX_LOOK_AHEAD_STEP);
    }

    private long lvProducerIndex() {
        return UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
    }

    private long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }

    private void soProducerIndex(long v) {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, v);
    }

    private void soConsumerIndex(long v) {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, v);
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
    
    @Override
    public long currentProducerIndex() {
        return lvProducerIndex();
    }
    
    @Override
    public long currentConsumerIndex() {
        return lvConsumerIndex();
    }
}
