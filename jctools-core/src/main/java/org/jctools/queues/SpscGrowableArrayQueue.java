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
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

import java.lang.reflect.Field;
import java.util.AbstractQueue;
import java.util.Iterator;


abstract class SpscGrowableArrayQueuePrePad<E> extends AbstractQueue<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12;
    // p13, p14, p15, p16, p17; drop 4 longs, the cold fields act as buffer
}
abstract class SpscGrowableArrayQueueProducerColdFields<E> extends SpscGrowableArrayQueuePrePad<E> {
    protected int maxQueueCapacity;
    protected int producerLookAheadStep;
    protected long producerLookAhead;
    protected long producerMask;
    protected E[] producerBuffer;
}
abstract class SpscGrowableArrayQueueProducerFields<E> extends SpscGrowableArrayQueueProducerColdFields<E> {
    protected long producerIndex;
}

abstract class SpscGrowableArrayQueueL2Pad<E> extends SpscGrowableArrayQueueProducerFields<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class SpscGrowableArrayQueueConsumerFields<E> extends SpscGrowableArrayQueueL2Pad<E> {
    protected long consumerMask;
    protected E[] consumerBuffer;
    protected long consumerIndex;
}

public class SpscGrowableArrayQueue<E> extends SpscGrowableArrayQueueConsumerFields<E>
    implements QueueProgressIndicators {
    private final static long P_INDEX_OFFSET;
    private final static long C_INDEX_OFFSET;
    static {
        try {
            Field iField = SpscGrowableArrayQueueProducerFields.class.getDeclaredField("producerIndex");
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = SpscGrowableArrayQueueConsumerFields.class.getDeclaredField("consumerIndex");
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private final static Object JUMP = new Object();
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
        producerLookAhead = mask - 1; // we know it's all empty to start with
        soProducerIndex(0L);// serves as a StoreStore barrier to support correct publication
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
            throw new NullPointerException();
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
        return offerColdPath(e, buffer, index, mask, offset);
    }

    private boolean offerColdPath(final E e, final E[] buffer, final long index, final long mask,
            final long offset) {
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
                final int newCapacity = (int) (2 * (mask + 1));
                final E[] newBuffer = allocate(newCapacity + 1);
                producerBuffer = newBuffer;
                producerMask = (long) (newCapacity - 1);
                if (newCapacity == maxCapacity) {
                    long currConsumerIndex = lvConsumerIndex();
                    // use lookAheadStep to store the consumer distance from final buffer
                    producerLookAheadStep = -(int) (index - currConsumerIndex);
                    producerLookAhead = currConsumerIndex + maxCapacity - 1;
                } else {
                    producerLookAhead = index + producerMask - 1;
                    adjustLookAheadStep(newCapacity);
                }
                final long offsetInNew = calcElementOffset(index, producerMask);
                soProducerIndex(index + 1);// this ensures correctness on 32bit platforms
                soElement(newBuffer, offsetInNew, e);// StoreStore
                soElement(buffer, nextArrayOffset(mask), newBuffer); // new buffer is visible after element is inserted
                soElement(buffer, offset, JUMP); // new buffer is visible after element is inserted // double the buffer and link old to new
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
    }

    private void writeToQueue(final E[] buffer, final E e, final long index, final long offset) {
        soProducerIndex(index + 1);// this ensures atomic write of long on 32bit platforms
        soElement(buffer, offset, e);// StoreStore
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
        final long offset = calcElementOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        if (null != e) {
            if(e == JUMP) {
                final E[] nextBuffer = getNextBuffer(buffer, mask);
                return newBufferPoll(nextBuffer, index);
            }
            soConsumerIndex(index + 1);// this ensures size correctness on 32bit platforms
            soElement(buffer, offset, null);// StoreStore
        }
        return (E) e;
    }

    @SuppressWarnings("unchecked")
    private E[] getNextBuffer(final E[] buffer, final long mask) {
        return (E[]) lvElement(buffer, nextArrayOffset(mask));
    }

    private long nextArrayOffset(final long mask) {
        return calcElementOffset(mask+1,mask<<1 + 1);
    }

    private E newBufferPoll(E[] nextBuffer, final long index) {
        consumerBuffer = nextBuffer;
        final long newMask = nextBuffer.length - 2;
        consumerMask = newMask;
        final long offsetInNew = calcElementOffset(index, newMask);
        final E n = lvElement(nextBuffer, offsetInNew);// LoadLoad
        if (null == n) {
            throw new IllegalStateException("new buffer must have at least one element");
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
        final long offset = calcElementOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        if (null == e) {
            return null;
        }
        if (e == JUMP) {
            return newBufferPeek(getNextBuffer(buffer,mask), index);
        }
        return (E) e;
    }

    private E newBufferPeek(E[] nextBuffer, final long index) {
        consumerBuffer = nextBuffer;
        final long newMask = nextBuffer.length - 2;
        consumerMask = newMask;
        final long offsetInNew = calcElementOffset(index, newMask);
        return lvElement(nextBuffer, offsetInNew);// LoadLoad
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
        producerLookAheadStep = Math.min(capacity / 4, SpscArrayQueue.MAX_LOOK_AHEAD_STEP);
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


    @Override
    public long currentProducerIndex() {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex() {
        return lvConsumerIndex();
    }
}
