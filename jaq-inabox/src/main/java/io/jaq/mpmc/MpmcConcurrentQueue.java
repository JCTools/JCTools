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
package io.jaq.mpmc;

import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.util.Pow2;
import io.jaq.util.UnsafeAccess;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

abstract class MpmcConcurrentQueueL0Pad {
    public long p00, p01, p02, p03, p04, p05, p06, p07;
    public long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpmcConcurrentQueueColdFields<E> extends MpmcConcurrentQueueL0Pad {
    protected static final int BUFFER_PAD = 32;
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 0);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    public MpmcConcurrentQueueColdFields(int capacity) {
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

abstract class MpmcConcurrentQueueL1Pad<E> extends MpmcConcurrentQueueColdFields<E> {
    public long p10, p11, p12, p13, p14, p15, p16;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueTailField<E> extends MpmcConcurrentQueueL1Pad<E> {
    protected volatile long tail;

    public MpmcConcurrentQueueTailField(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueL2Pad<E> extends MpmcConcurrentQueueTailField<E> {
    public long p20, p21, p22, p23, p24, p25, p26;
    public long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpmcConcurrentQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueHeadField<E> extends MpmcConcurrentQueueL2Pad<E> {
    protected long head;

    public MpmcConcurrentQueueHeadField(int capacity) {
        super(capacity);
    }
}

abstract class MpmcConcurrentQueueL3Pad<E> extends MpmcConcurrentQueueHeadField<E> {
    protected final static long TAIL_OFFSET;
    protected final static long HEAD_OFFSET;
    protected static final long ARRAY_BASE;
    protected static final int ELEMENT_SHIFT;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcConcurrentQueueTailField.class
                    .getDeclaredField("tail"));
            HEAD_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcConcurrentQueueHeadField.class
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

    public MpmcConcurrentQueueL3Pad(int capacity) {
        super(capacity);
    }
}

public final class MpmcConcurrentQueue<E> extends MpmcConcurrentQueueL3Pad<E> implements Queue<E>,
        ConcurrentQueue<E>, ConcurrentQueueProducer<E>, ConcurrentQueueConsumer<E> {

    public MpmcConcurrentQueue(final int capacity) {
        super(capacity);
    }

    private long getHeadV() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET);
    }

    private long getTailV() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET);
    }

    private boolean casTail(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
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

    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        long currentTail;
        do {
            currentTail = getTailV();
            final long wrapPoint = currentTail - capacity;
            if (getHeadV() <= wrapPoint) { // volatile read of head
                return false;
            }
        } while (!casTail(currentTail, currentTail + 1));
        final long offset = offset(currentTail);
        // head may become visible before element is taken
        final E[] lb = buffer;
        while (UnsafeAccess.UNSAFE.getObjectVolatile(lb, offset) != null);
        UnsafeAccess.UNSAFE.putOrderedObject(lb, offset, e);
        return true;
    }

    public int offerStatus(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        long currentTail;
        currentTail = getTailV();
        final long wrapPoint = currentTail - capacity;
        if (getHeadV() <= wrapPoint) { // volatile read of head
            return 1;
        }
        if (!casTail(currentTail, currentTail + 1)) {
            return -1;
        }
        final long offset = offset(currentTail);
        // head may become visible before element is taken
        final E[] lb = buffer;
        while (UnsafeAccess.UNSAFE.getObjectVolatile(lb, offset) != null);
        UnsafeAccess.UNSAFE.putOrderedObject(lb, offset, e);
        return 0;
    }
    @SuppressWarnings("unchecked")
    public E poll() {
        final long currentTail = getTailV();
        long currentHead;
        do {
            currentHead = getHeadV();
            if (currentHead >= currentTail) {
                return null;
            }
        } while (!casHead(currentHead, currentHead + 1) && currentHead < currentTail);
        // tail may become visible before element
        final long offset = offset(currentHead);
        E e;
        final E[] lb = buffer;
        do {
            e = (E) UnsafeAccess.UNSAFE.getObjectVolatile(lb, offset);
        } while (e == null);
        UnsafeAccess.UNSAFE.putOrderedObject(lb, offset, null);
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
