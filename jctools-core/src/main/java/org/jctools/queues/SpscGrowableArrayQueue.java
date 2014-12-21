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
    protected int maxSize;
    protected int producerLookAheadStep;
    protected long producerIndex;
    protected long producerLookAhead;
    protected long producerMask;
    protected E[] producerBuffer;
}

abstract class SpscGrowableArrayQueueL2Pad<E> extends SpscGrowableArrayQueueProducerFields<E> {
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class SpscGrowableArrayQueueConsumerField<E> extends SpscGrowableArrayQueueL2Pad<E> {
    protected long consumerIndex;
    protected long consumerMask;
    protected E[] consumerBuffer;
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

    @SuppressWarnings("unchecked")
    public SpscGrowableArrayQueue(final int maxCapacity) {
        this(Math.min(Pow2.roundToPowerOfTwo(maxCapacity / 2), 16), maxCapacity);
    }

    public SpscGrowableArrayQueue(final int initialCapacity, int maxCapacity) {
        if (initialCapacity >= maxCapacity) {
            throw new IllegalArgumentException("Initial capacity cannot exceed maximum capacity");
        }

        int p2capacity = Pow2.roundToPowerOfTwo(initialCapacity);
        long mask = p2capacity - 1;
        // last slot used for linking the arrays
        E[] buffer = (E[]) new Object[p2capacity + 1];
        producerBuffer = buffer;
        producerMask = mask;
        adjustLookAheadStep(p2capacity);
        consumerBuffer = buffer;
        consumerMask = mask;
        maxSize = Pow2.roundToPowerOfTwo(maxCapacity);
        producerLookAhead = mask; // we know it's all empty to start with
        soProducerIndex(0l);
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        // local load of field to avoid repeated loads after volatile reads
        E[] buffer = producerBuffer;
        final long index = producerIndex;
        final long mask = producerMask;
        final long offset = calcElementOffset(index, mask);
        if (index >= producerLookAhead) {
            final int lookAheadStep = producerLookAheadStep;
            // normal case, go around the buffer or resize if full (unless we hit max capacity)
            if (lookAheadStep > 0) {
                long lookAheadElementOffset = calcElementOffset(index + lookAheadStep, mask);
                if (null == lvElement(buffer, lookAheadElementOffset)) {// LoadLoad
                    producerLookAhead = index + lookAheadStep; // joy, there's room
                } else if (null != lvElement(buffer, offset)) { // buffer is full
                    int currCapacity = (int) (mask + 1);
                    if (currCapacity != maxSize) {
                        resize(buffer, index, maxSize, e); // double the buffer and link old to new
                        return true;
                    } else {
                        // we're full and can't grow
                        return false;
                    }
                }
            }
            // the step is negative (or zero) in the period between allocating the max sized buffer and the
            // consumer starting on it
            else {
                final int prevElementsInOtherBuffers = -lookAheadStep;
                // until the consumer starts using the current buffer we need to check consumer index to
                // verify size
                long currConsumerIndex = lvConsumerIndex();
                int size = (int) (index - currConsumerIndex);
                int maxCapacity = (int) (mask + 1); // we're on max capacity or we wouldn't be here
                if (size == maxCapacity) {
                    // consumer index has not changed since adjusting the lookAhead index, we're full
                    return false;
                }
                // if consumerIndex progressed enough so that current size indicates it is on same buffer
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
            }
        }
        soProducerIndex(index + 1);// this ensures correctness on 32bit platforms
        soElement(buffer, offset, e);// StoreStore
        return true;
    }

    @SuppressWarnings("unchecked")
    private void resize(final E[] old, final long currIndex,  final int maxCapacity, final E e) {
        final int newCapacity = 2 * (old.length - 1);
        final E[] newBuffer = (E[]) new Object[newCapacity + 1];
        producerBuffer = newBuffer;
        final long newMask = newCapacity - 1;
        producerMask = newMask;
        setNextBufferPointer(old, producerBuffer);

        if (newCapacity == maxCapacity) {
            long currConsumerIndex = lvConsumerIndex();
            // use lookAheadStep to store the consumer distance from final buffer
            producerLookAheadStep = -(int) (currIndex - currConsumerIndex);
            producerLookAhead = currConsumerIndex + maxCapacity;

        } else {
            producerLookAhead = currIndex + producerMask;
            adjustLookAheadStep(newCapacity);
        }
        soProducerIndex(currIndex + 1);// this ensures correctness on 32bit platforms
        final long offset = calcElementOffset(currIndex, newMask);
        soElement(newBuffer, offset, e);// StoreStore
    }

    private void setNextBufferPointer(E[] buffer, Object next) {
        ((Object[]) buffer)[buffer.length - 1] = next;
    }

    @SuppressWarnings("unchecked")
    private E[] getNextBufferPointer(E[] buffer) {
        return (E[]) ((Object[]) buffer)[buffer.length - 1];
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E poll() {
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long offset = calcElementOffset(index, consumerMask);
        final E e = lvElement(buffer, offset);// LoadLoad
        if (null == e) {
            E[] nextBuffer = getNextBufferPointer(buffer);
            if (nextBuffer == null) {
                return null;
            }
            else {
                return newBufferPoll(nextBuffer, index);
            }
        }
        soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
        soElement(buffer, offset, null);// StoreStore
        return e;
    }

    private E newBufferPoll(E[] nextBuffer, final long index) {
        consumerBuffer = nextBuffer;
        final long newMask = nextBuffer.length - 2;
        consumerMask = newMask;
        final long offsetInNew = calcElementOffset(index, newMask);
        final E n = lvElement(nextBuffer, offsetInNew);// LoadLoad
        if (null == n) {
            return null;
        }
        else {
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
    @Override
    public E peek() {
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long offset = calcElementOffset(index, consumerMask);
        final E e = lvElement(buffer, offset);// LoadLoad
        if (null == e) {
            E[] nextBuffer = getNextBufferPointer(buffer);
            if (nextBuffer == null) {
                return null;
            }
            else {
                return newBufferPeek(nextBuffer, index);
            }
        }
        return e;
    }
    private E newBufferPeek(E[] nextBuffer, final long index) {
        consumerBuffer = nextBuffer;
        final long newMask = nextBuffer.length - 2;
        consumerMask = newMask;
        final long offsetInNew = calcElementOffset(index, newMask);
        return lvElement(nextBuffer, offsetInNew);// LoadLoad
    }
    @Override
    public int size() {
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

    @SuppressWarnings("unchecked")
    private static final <E> E lvElement(E[] buffer, long offset) {
        return (E) UNSAFE.getObjectVolatile(buffer, offset);
    }
}
