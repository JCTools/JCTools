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
package io.jaq.spmc;

import static io.jaq.util.UnsafeAccess.UNSAFE;
import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.util.Pow2;
import io.jaq.util.UnsafeAccess;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

abstract class SpmcConcurrentArrayQueueL0Pad {
    public long p00, p01, p02, p03, p04, p05, p06, p07;
    public long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class SpmcConcurrentArrayQueueColdFields<E> extends SpmcConcurrentArrayQueueL0Pad {
    protected static final int BUFFER_PAD = 32;
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 0);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    public SpmcConcurrentArrayQueueColdFields(int capacity) {
        if (Pow2.isPowerOf2(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = Pow2.findNextPositivePowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        // pad data on either end with some empty slots.
        buffer = (E[]) new Object[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
    }
}

abstract class SpmcConcurrentArrayQueueL1Pad<E> extends SpmcConcurrentArrayQueueColdFields<E> {
    public long p10, p11, p12, p13, p14, p15, p16;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpmcConcurrentArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpmcConcurrentArrayQueueTailField<E> extends SpmcConcurrentArrayQueueL1Pad<E> {
    protected long tail;
    protected long batchTail;

    public SpmcConcurrentArrayQueueTailField(int capacity) {
        super(capacity);
    }
}

abstract class SpmcConcurrentArrayQueueL2Pad<E> extends SpmcConcurrentArrayQueueTailField<E> {
    public long p20, p21, p22, p23, p24, p25, p26;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpmcConcurrentArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpmcConcurrentArrayQueueHeadField<E> extends SpmcConcurrentArrayQueueL2Pad<E> {
    protected long head;

    public SpmcConcurrentArrayQueueHeadField(int capacity) {
        super(capacity);
    }
}

abstract class SpmcConcurrentArrayQueueL3Pad<E> extends SpmcConcurrentArrayQueueHeadField<E> {
    protected final static long TAIL_OFFSET;
    protected final static long HEAD_OFFSET;
    protected static final long ARRAY_BASE;
    protected static final int ELEMENT_SHIFT;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(SpmcConcurrentArrayQueueTailField.class
                    .getDeclaredField("tail"));
            HEAD_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(SpmcConcurrentArrayQueueHeadField.class
                    .getDeclaredField("head"));
            final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
            if (4 == scale) {
                ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
            } else if (8 == scale) {
                ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
            } else {
                throw new IllegalStateException("Unknown pointer size");
            }
            // Including the buffer pad in the array base offset
            ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class)
                    + (BUFFER_PAD << (ELEMENT_SHIFT - SPARSE_SHIFT));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    public long p40, p41, p42, p43, p44, p45, p46;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpmcConcurrentArrayQueueL3Pad(int capacity) {
        super(capacity);
    }
}

public final class SpmcConcurrentQueue<E> extends SpmcConcurrentArrayQueueL3Pad<E> implements Queue<E>,
        ConcurrentQueue<E>, ConcurrentQueueConsumer<E>, ConcurrentQueueProducer<E> {
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);

    public SpmcConcurrentQueue(final int capacity) {
        super(capacity);
    }

    private long getHeadV() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET);
    }

    private void tailLazySet(long v) {
        UnsafeAccess.UNSAFE.putOrderedLong(this, TAIL_OFFSET, v);
    }

    private long getTailV() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET);
    }

    private boolean casHead(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, HEAD_OFFSET, expect, newValue);
    }

    public boolean add(final E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("Queue is full");
    }

    private long offset(long index) {
        return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
    }

    /**
     * This is FFBuffer style offer checking nullity to ensure the next slot is free. The tail counter must
     * still be correctly published so we have to lazy set it. Note that the head counter is not examined.
     */
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        if (tail >= batchTail) {
            if (null != UNSAFE.getObjectVolatile(buffer, offset(tail + OFFER_BATCH_SIZE))) {
                return false;
            }
            batchTail = tail + OFFER_BATCH_SIZE;
        }
        // tail must be published to be visible to the consumers
        UNSAFE.putObject(buffer, offset(tail), e);
        tailLazySet(tail + 1);

        return true;
    }

    /**
     * Using head counter as the entry point to the queue.
     */
    public E poll() {
        final long currentTail = getTailV();
        long currentHead;
        do {
            currentHead = getHeadV();
            if (currentHead >= currentTail) {
                return null;
            }
        } while (!casHead(currentHead, currentHead + 1) && currentHead < currentTail);
        final long offset = offset(currentHead);
        @SuppressWarnings("unchecked")
        final E e = (E) UnsafeAccess.UNSAFE.getObject(buffer, offset);
        UnsafeAccess.UNSAFE.putOrderedObject(buffer, offset, null);
        return e;

    }

    public E remove() {
        final E e = poll();
        if (null == e) {
            throw new NoSuchElementException("Queue is empty");
        }

        return e;
    }

    public E element() {
        final E e = peek();
        if (null == e) {
            throw new NoSuchElementException("Queue is empty");
        }

        return e;
    }

    public E peek() {
        long currentHead = getHeadV();
        return getElement(currentHead);
    }

    @SuppressWarnings("unchecked")
    private E getElement(long index) {
        return (E) UnsafeAccess.UNSAFE.getObject(buffer, offset(index));
    }

    public int size() {
        return (int) (getTailV() - getHeadV());
    }

    public boolean isEmpty() {
        return getTailV() == getHeadV();
    }

    public boolean contains(final Object o) {
        if (null == o) {
            return false;
        }

        for (long i = getHeadV(), limit = getTailV(); i < limit; i++) {
            final E e = getElement(i);
            if (o.equals(e)) {
                return true;
            }
        }

        return false;
    }

    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    public <T> T[] toArray(final T[] a) {
        throw new UnsupportedOperationException();
    }

    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(final Collection<?> c) {
        for (final Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }

        return true;
    }

    public boolean addAll(final Collection<? extends E> c) {
        for (final E e : c) {
            add(e);
        }

        return true;
    }

    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        Object value;
        do {
            value = poll();
        } while (null != value);
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        return this;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        return this;
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
