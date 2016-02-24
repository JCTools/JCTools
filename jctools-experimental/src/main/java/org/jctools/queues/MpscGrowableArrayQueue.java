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
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeRefArrayAccess.REF_ARRAY_BASE;
import static org.jctools.util.UnsafeRefArrayAccess.REF_ELEMENT_SHIFT;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;
import static org.jctools.util.UnsafeRefArrayAccess.spElement;

import java.lang.reflect.Field;
import java.util.AbstractQueue;
import java.util.Iterator;

import org.jctools.util.Pow2;

abstract class MpscGrowableArrayQueuePad1<E> extends AbstractQueue<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscGrowableArrayQueueProducerFields<E> extends MpscGrowableArrayQueuePad1<E> {
    protected long producerIndex;
}

abstract class MpscGrowableArrayQueuePad2<E> extends MpscGrowableArrayQueueProducerFields<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscGrowableArrayQueueColdProducerFields<E> extends MpscGrowableArrayQueuePad2<E> {
    protected long maxQueueCapacity;
    protected long producerMask;
    protected E[] producerBuffer;
    protected volatile long consumerIndexCache;

}

abstract class MpscGrowableArrayQueuePad3<E> extends MpscGrowableArrayQueueColdProducerFields<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscGrowableArrayQueueConsumerFields<E> extends MpscGrowableArrayQueuePad3<E> {
    protected long consumerMask;
    protected E[] consumerBuffer;
    protected long consumerIndex;
}

/**
 * An MPSC array queue which starts at <i>initialCapacity</i> and grows to <i>maxCapacity</i> in mutiples of
 * 2. The queue grows only when the current buffer is full and elements are not copied on resize, instead a
 * link to the new buffer is stored in the old buffer for the consumer to follow.<br>
 *
 *
 * @param <E>
 */
public class MpscGrowableArrayQueue<E> extends MpscGrowableArrayQueueConsumerFields<E>
        implements QueueProgressIndicators {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    private final static long P_INDEX_OFFSET;
    private final static long C_INDEX_OFFSET;
    private final static long C_INDEX_CACHE_OFFSET;

    static {
        try {
            Field iField = MpscGrowableArrayQueueProducerFields.class.getDeclaredField("producerIndex");
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = MpscGrowableArrayQueueConsumerFields.class.getDeclaredField("consumerIndex");
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = MpscGrowableArrayQueueColdProducerFields.class
                    .getDeclaredField("consumerIndexCache");
            C_INDEX_CACHE_OFFSET = UNSAFE.objectFieldOffset(iField);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private final static Object JUMP = new Object();

    public MpscGrowableArrayQueue(final int maxCapacity) {
        this(Math.min(Pow2.roundToPowerOfTwo(maxCapacity / 8), 16), maxCapacity);
    }

    public MpscGrowableArrayQueue(final int initialCapacity, int maxCapacity) {
        if (initialCapacity >= maxCapacity) {
            throw new IllegalArgumentException("Initial capacity cannot exceed maximum capacity");
        }

        int p2capacity = Pow2.roundToPowerOfTwo(initialCapacity);
        // leave lower bit of mask clear
        long mask = (p2capacity - 1) << 1;
        // need extra element to point at next array
        E[] buffer = allocate(p2capacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        maxQueueCapacity = Pow2.roundToPowerOfTwo(maxCapacity) << 1;
        soConsumerIndexCache(0); // we know it's all empty to start with
    }

    @Override
    public final Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        long mask;
        E[] buffer;
        long currentProducerIndex;
        long consumerIndexCache = lvConsumerIndexCache();

        while (true) {
            // lower bit is indicative of resize, if we see it we spin until it's cleared
            while (((currentProducerIndex = lvProducerIndex()) & 1) == 1)
                ;
            // now we have a pIndex which is even (lower bit is 0)

            // mask/buffer may get changed by resizing. Only used after successful CAS.
            mask = this.producerMask;
            buffer = this.producerBuffer;
            final boolean atMaxCapacity = mask + 2 == maxQueueCapacity;
            final long currCapacity = atMaxCapacity ? mask + 1 : mask;

            final long wrapPoint = (currentProducerIndex - currCapacity);
            if (consumerIndexCache <= wrapPoint) {
                consumerIndexCache = lvConsumerIndex();
                if (consumerIndexCache > wrapPoint) {
                    soConsumerIndexCache(consumerIndexCache);
                }
                // full and cannot grow
                else if (atMaxCapacity) {
                    return false;
                }
                // resize -> set lower bit
                else if (casProducerIndex(currentProducerIndex, currentProducerIndex + 1)) {
                    resize(currentProducerIndex, buffer, mask, e);
                    return true;
                }
                else {
                    continue; // skip CAS, no point
                }
            }
            if (casProducerIndex(currentProducerIndex, currentProducerIndex + 2)) {
                break;
            }
        }

        final long offset = calcElementOffset(currentProducerIndex, mask);
        soElement(buffer, offset, e);
        return true;
    }

    private void resize(long currentProducerIndex, E[] buffer, long mask, final E e) {
        final int newCapacity = (int) (2 * ((mask >> 1) + 1));
        final E[] newBuffer = allocate(newCapacity + 1);
        producerBuffer = newBuffer;
        producerMask = (long) (newCapacity - 1) << 1;
        final long offsetInOld = calcElementOffset(currentProducerIndex, mask);
        final long offsetInNew = calcElementOffset(currentProducerIndex, producerMask);
        spElement(buffer, nextArrayOffset(mask), newBuffer);
        spElement(newBuffer, offsetInNew, e);

        // make resize visible to consumer
        soElement(buffer, offsetInOld, JUMP);

        // make resize visible to the other producers
        soProducerIndex(currentProducerIndex + 2);
    }

    private static long calcElementOffset(long index, long mask) {
        return REF_ARRAY_BASE + ((index & mask) << (REF_ELEMENT_SHIFT - 1));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public final E poll() {
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long mask = consumerMask;

        final long offset = calcElementOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        if (e != null) {
            if (e == JUMP) {
                final E[] nextBuffer = getNextBuffer(buffer, mask);
                return newBufferPoll(nextBuffer, index);
            }
            soElement(buffer, offset, null);
            soConsumerIndex(index + 2);
        }
        return (E) e;
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
        if (e == JUMP) {
            return newBufferPeek(getNextBuffer(buffer, mask), index);
        }
        return (E) e;
    }

    @SuppressWarnings("unchecked")
    private E[] getNextBuffer(final E[] buffer, final long mask) {
        return (E[]) lvElement(buffer, nextArrayOffset(mask));
    }

    private long nextArrayOffset(final long mask) {
        return calcElementOffset(mask + 2, Long.MAX_VALUE);
    }

    private E newBufferPoll(E[] nextBuffer, final long index) {
        final long offsetInNew = newBufferAndOffset(nextBuffer, index);
        final E n = lvElement(nextBuffer, offsetInNew);// LoadLoad
        if (null == n) {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        soElement(nextBuffer, offsetInNew, null);// StoreStore
        soConsumerIndex(index + 2);
        return n;
    }

    private E newBufferPeek(E[] nextBuffer, final long index) {
        final long offsetInNew = newBufferAndOffset(nextBuffer, index);
        final E n = lvElement(nextBuffer, offsetInNew);// LoadLoad
        if (null == n) {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        return n;
    }

    private long newBufferAndOffset(E[] nextBuffer, final long index) {
        consumerBuffer = nextBuffer;
        consumerMask = (nextBuffer.length - 2) << 1;
        final long offsetInNew = calcElementOffset(index, consumerMask);
        return offsetInNew;
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
                return (int) (currentProducerIndex - after) >> 1;
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

    private boolean casProducerIndex(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }

    private void soConsumerIndex(long v) {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, v);
    }

    private long lvConsumerIndexCache() {
        return consumerIndexCache;
    }

    protected final void soConsumerIndexCache(long v) {
        UNSAFE.putOrderedLong(this, C_INDEX_CACHE_OFFSET, v);
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
