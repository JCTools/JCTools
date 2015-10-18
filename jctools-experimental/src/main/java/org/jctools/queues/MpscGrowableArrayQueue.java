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
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

import java.lang.reflect.Field;
import java.util.AbstractQueue;
import java.util.Iterator;

import org.jctools.util.Pow2;
import org.jctools.util.UnsafeRefArrayAccess;

abstract class MpscGrowableArrayQueuePrePad<E> extends AbstractQueue<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7, p8;
    long p10, p11, p12, p13;
}
abstract class MpscGrowableArrayQueueProducerColdFields<E> extends MpscGrowableArrayQueuePrePad<E> {
    protected int maxQueueCapacity;
    protected long producerMask;
    protected E[] producerBuffer;
}
abstract class MpscGrowableArrayQueueProducerFields<E> extends MpscGrowableArrayQueueProducerColdFields<E> {
    protected long producerIndex;
}
abstract class MpscGrowableArrayQueueMidPad<E> extends MpscGrowableArrayQueueProducerFields<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscGrowableArrayQueueHeadCacheField<E> extends MpscGrowableArrayQueueMidPad<E> {
    private volatile long consumerIndexCache;

    protected final long lvConsumerIndexCache() {
        return consumerIndexCache;
    }

    protected final void svConsumerIndexCache(long v) {
        consumerIndexCache = v;
    }
}
abstract class MpscGrowableArrayQueueL2Pad<E> extends MpscGrowableArrayQueueHeadCacheField<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscGrowableArrayQueueConsumerFields<E> extends MpscGrowableArrayQueueL2Pad<E> {
    protected long consumerMask;
    protected E[] consumerBuffer;
    protected long consumerIndex;
}

public class MpscGrowableArrayQueue<E> extends MpscGrowableArrayQueueConsumerFields<E> 
    implements QueueProgressIndicators {
    private final static long P_INDEX_OFFSET;
    private final static long C_INDEX_OFFSET;
    static {
        try {
            Field iField = MpscGrowableArrayQueueProducerFields.class.getDeclaredField("producerIndex");
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = MpscGrowableArrayQueueConsumerFields.class.getDeclaredField("consumerIndex");
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private final static Object JUMP = new Object();
    public MpscGrowableArrayQueue(final int maxCapacity) {
        this(Math.min(Pow2.roundToPowerOfTwo(maxCapacity / 2), 16), maxCapacity);
    }

    public MpscGrowableArrayQueue(final int initialCapacity, int maxCapacity) {
        if (initialCapacity >= maxCapacity) {
            throw new IllegalArgumentException("Initial capacity cannot exceed maximum capacity");
        }

        int p2capacity = Pow2.roundToPowerOfTwo(initialCapacity);
        long mask = p2capacity - 1;
        // need extra element to point at next array
        E[] buffer = allocate(p2capacity+1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        maxQueueCapacity = Pow2.roundToPowerOfTwo(maxCapacity);
        svConsumerIndexCache(mask - 1); // we know it's all empty to start with
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
    /**
     * {@inheritDoc} <br>
     * 
     * IMPLEMENTATION NOTES:<br>
     * Lock free offer using a single CAS. As class name suggests access is permitted to many threads
     * concurrently.
     * 
     * @see java.util.Queue#offer(java.lang.Object)
     * @see MessagePassingQueue#offer(Object)
     */
    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        // use a cached view on consumer index (potentially updated in loop)
        final long mask = this.producerMask;
        final long capacity = mask + 1;
        long consumerIndexCache = lvConsumerIndexCache(); // LoadLoad
        long currentProducerIndex;
        do {
            currentProducerIndex = lvProducerIndex(); // LoadLoad
            final long wrapPoint = currentProducerIndex - capacity;
            if (consumerIndexCache <= wrapPoint) {
                final long currHead = lvConsumerIndex(); // LoadLoad
                if (currHead <= wrapPoint) {
                    if (capacity == maxQueueCapacity) {
                        return false; // FULL :(
                    }
                    else {
                        // resize
                    }
                } else {
                    // update shared cached value of the consumerIndex
                    svConsumerIndexCache(currHead); // StoreLoad
                    // update on stack copy, we might need this value again if we lose the CAS.
                    consumerIndexCache = currHead;
                }
            }
        } while (!casProducerIndex(currentProducerIndex, currentProducerIndex + 1));

        // Won CAS, move on to storing
        final long offset = calcElementOffset(currentProducerIndex, mask);
        UnsafeRefArrayAccess.soElement(producerBuffer, offset, e); // StoreStore
        return true; // AWESOME :)
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

    private long lvProducerIndex() {
        return UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
    }

    private long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }

    private void soProducerIndex(long v) {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, v);
    }
    protected final boolean casProducerIndex(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
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
