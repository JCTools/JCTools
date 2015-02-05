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

abstract class SpscGrowableArrayQueueProducerFields<E> extends AbstractQueue<E> {
    protected long producerIndex;
}

abstract class SpscGrowableArrayQueueProducerColdFields<E> extends SpscGrowableArrayQueueProducerFields<E> {
    protected int maxQueueCapacity;
    protected int producerLookAheadStep;
    protected long producerLookAhead;
    protected long producerMask;
    protected E[] producerBuffer;
}

abstract class SpscGrowableArrayQueueL2Pad<E> extends SpscGrowableArrayQueueProducerColdFields<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12;
}

abstract class SpscGrowableArrayQueueConsumerColdField<E> extends SpscGrowableArrayQueueL2Pad<E> {
    protected long consumerMask;
    protected E[] consumerBuffer;
}

abstract class SpscGrowableArrayQueueConsumerField<E> extends SpscGrowableArrayQueueConsumerColdField<E> {
    protected long consumerIndex;
}

public class SpscGrowableArrayQueue<E> extends SpscGrowableArrayQueueConsumerField<E> {
    private static final int MAX_LOOK_AHEAD_STEP = Integer
            .getInteger("jctools.spsc.max.lookahead.step", 4096);
    private final static long P_INDEX_OFFSET;
    private final static long C_INDEX_OFFSET;
    private static final long REF_ARRAY_BASE;
    private static final int REF_ELEMENT_SHIFT;
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
            Field iField = SpscGrowableArrayQueueProducerFields.class.getDeclaredField("producerIndex");
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = SpscGrowableArrayQueueConsumerField.class.getDeclaredField("consumerIndex");
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static class NextHolder {
        final Object[] next;

        public NextHolder(Object[] next) {
            this.next = next;
        }
        
    }
    public SpscGrowableArrayQueue(final int maxCapacity) {
        this(Math.min(Pow2.roundToPowerOfTwo(maxCapacity / 2), 16), maxCapacity);
    }

    @SuppressWarnings("unchecked")
    public SpscGrowableArrayQueue(final int initialCapacity, int maxCapacity) {
        if (initialCapacity >= maxCapacity) {
            throw new IllegalArgumentException("Initial capacity cannot exceed maximum capacity");
        }

        int p2capacity = Pow2.roundToPowerOfTwo(initialCapacity);
        long mask = p2capacity - 1;
        E[] buffer = (E[]) new Object[p2capacity];
        producerBuffer = buffer;
        producerMask = mask;
        adjustLookAheadStep(p2capacity);
        consumerBuffer = buffer;
        consumerMask = mask;
        maxQueueCapacity = Pow2.roundToPowerOfTwo(maxCapacity);
        producerLookAhead = mask - 1; // we know it's all empty to start with
        soProducerIndex(0l);// serves as a StoreStore barrier to support correct publication
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
        final long offset = calcElementOffset(index, mask);
        // expected hot path
        if (index < producerLookAhead) {
            writeToQueue(buffer, e, index, offset);
            return true;
        } 
        final int lookAheadStep = producerLookAheadStep;
        // normal case, go around the buffer or resize if full (unless we hit max capacity)
        if (lookAheadStep > 0) {
            long lookAheadElementOffset = calcElementOffset(index + lookAheadStep, mask);
            // Try and look ahead a number of elements so we don't have to do this all the time
            if (null == lvElement(buffer, lookAheadElementOffset)) {
                producerLookAhead = index + lookAheadStep - 1; // joy, there's plenty of room
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
                resize(buffer, index, offset, maxCapacity, e); // double the buffer and link old to new
            }
            return true;
        }
        // the step is negative (or zero) in the period between allocating the max sized buffer and the
        // consumer starting on it
        else {
            return offerWhileWaitingForConsumerOnLastBuffer(buffer, e, index, mask, offset, lookAheadStep);
        }
    }

    private boolean offerWhileWaitingForConsumerOnLastBuffer(final E[] buffer, final E e, final long index,
            final long mask, final long offset, final int lookAheadStep) {
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
        // if consumerInde x progressed enough so that current size indicates it is on same buffer
        long firstIndexInCurrentBuffer = producerLookAhead - maxCapacity + prevElementsInOtherBuffers;
        if (currConsumerIndex >= firstIndexInCurrentBuffer) {
            // job done, we've now settled into our final state
            adjustLookAheadStep(maxCapacity);
        }
        // consumer is still on some other buffer
        else {
            // how many elements out of buffer?
            producerLookAheadStep = (int) (currConsumerIndex - firstIndexInCurrentBuffer);
        }
        producerLookAhead = currConsumerIndex + maxCapacity;
        writeToQueue(buffer, e, index, offset);
        return true;
    }

    private void writeToQueue(final E[] buffer, final E e, final long index, final long offset) {
        soProducerIndex(index + 1);// this ensures atomic write of long on 32bit platforms
        soElement(buffer, offset, e);// StoreStore
    }

    @SuppressWarnings("unchecked")
    private void resize(final E[] old, final long currIndex, final long offset, final int maxCapacity,
            final E e) {
        final int newCapacity = 2 * old.length;
        final E[] newBuffer = (E[]) new Object[newCapacity];
        producerBuffer = newBuffer;
        final long newMask = newCapacity - 1;
        producerMask = newMask;
        if (newCapacity == maxCapacity) {
            long currConsumerIndex = lvConsumerIndex();
            // use lookAheadStep to store the consumer distance from final buffer
            producerLookAheadStep = -(int) (currIndex - currConsumerIndex);
            producerLookAhead = currConsumerIndex + maxCapacity - 1;
        } else {
            producerLookAhead = currIndex + producerMask - 1;
            adjustLookAheadStep(newCapacity);
        }
        final long offsetInNew = calcElementOffset(currIndex, newMask);
        soProducerIndex(currIndex + 1);// this ensures correctness on 32bit platforms
        soElement(newBuffer, offsetInNew, e);// StoreStore
        soElement(old, offset, new NextHolder(newBuffer)); // new buffer is visible after element is inserted
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
        final long offset = calcElementOffset(index, consumerMask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        boolean isNextBuffer = e instanceof NextHolder;
        if (null != e && !isNextBuffer) {
            soConsumerIndex(index + 1);// this ensures size correctness on 32bit platforms
            soElement(buffer, offset, null);// StoreStore
            return (E) e;
        } else if (isNextBuffer) {
            return newBufferPoll((E[]) ((NextHolder) e).next, index);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private E newBufferPoll(E[] nextBuffer, final long index) {
        consumerBuffer = nextBuffer;
        final long newMask = nextBuffer.length - 1;
        consumerMask = newMask;
        final long offsetInNew = calcElementOffset(index, newMask);
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
        final long offset = calcElementOffset(index, consumerMask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        if (null == e) {
            return null;
        }
        if (e instanceof NextHolder) {
            return newBufferPeek((E[]) ((NextHolder) e).next, index);
        }
        return (E) e;
    }

    @SuppressWarnings("unchecked")
    private E newBufferPeek(E[] nextBuffer, final long index) {
        consumerBuffer = nextBuffer;
        final long newMask = nextBuffer.length - 1;
        consumerMask = newMask;
        final long offsetInNew = calcElementOffset(index, newMask);
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

    private static final long calcElementOffset(long index, long mask) {
        return REF_ARRAY_BASE + ((index & mask) << REF_ELEMENT_SHIFT);
    }

    private static final void soElement(Object[] buffer, long offset, Object e) {
        UNSAFE.putOrderedObject(buffer, offset, e);
    }

    private static final <E> Object lvElement(E[] buffer, long offset) {
        return UNSAFE.getObjectVolatile(buffer, offset);
    }
}
