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

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

abstract class InlinedRingBufferL0Pad {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class InlinedRingBufferColdFields<E> extends InlinedRingBufferL0Pad {
    protected static final int BUFFER_PAD = 32;
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 2);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    InlinedRingBufferColdFields(int capacity) {
        if (Pow2.isPowerOfTwo(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = Pow2.findNextPositivePowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        buffer = (E[]) new Object[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
    }
}

abstract class InlinedRingBufferL1Pad<E> extends InlinedRingBufferColdFields<E> {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p10, p11, p12, p13, p14, p15, p16;

    InlinedRingBufferL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class InlinedRingBufferOfferFields<E> extends InlinedRingBufferL1Pad<E> {
    protected volatile long tail;
    protected long headCache;

    InlinedRingBufferOfferFields(int capacity) {
        super(capacity);
    }
}

abstract class InlinedRingBufferL2Pad<E> extends InlinedRingBufferOfferFields<E> {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p30, p31, p32, p33, p34, p35, p36;

    InlinedRingBufferL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class InlinedRingBufferPollFields<E> extends InlinedRingBufferL2Pad<E> {
    protected volatile long head;
    protected long tailCache;

    InlinedRingBufferPollFields(int capacity) {
        super(capacity);
    }
}

abstract class InlinedRingBufferL3Pad<E> extends InlinedRingBufferPollFields<E> {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p50, p51, p52, p53, p54, p55, p56;

    InlinedRingBufferL3Pad(int capacity) {
        super(capacity);
    }
}

public final class InlinedCountersSpscConcurrentArrayQueue<E> extends InlinedRingBufferL3Pad<E> implements Queue<E> {
    private final static long TAIL_OFFSET;
    private final static long HEAD_OFFSET;
    private static final long ARRAY_BASE;
    private static final int ELEMENT_SHIFT;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(InlinedRingBufferOfferFields.class.getDeclaredField("tail"));
            HEAD_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(InlinedRingBufferPollFields.class.getDeclaredField("head"));
            final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);

            if (4 == scale) {
                ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
            } else if (8 == scale) {
                ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
            } else {
                throw new IllegalStateException("Unknown pointer size");
            }
            ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class)
                    + (BUFFER_PAD << (ELEMENT_SHIFT - SPARSE_SHIFT));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public InlinedCountersSpscConcurrentArrayQueue(final int capacity) {
        super(capacity);
    }

    private void headLazySet(long v) {
        UnsafeAccess.UNSAFE.putOrderedLong(this, HEAD_OFFSET, v);
    }

    private long getHead() {
        return head;
    }

    private void tailLazySet(long v) {
        UnsafeAccess.UNSAFE.putOrderedLong(this, TAIL_OFFSET, v);
    }

    private long getTail() {
        return tail;
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

        final long currentTail = getTail();
        final long wrapPoint = currentTail - capacity + 32;
        if (headCache <= wrapPoint) {
            headCache = getHead();
            if (headCache <= wrapPoint) {
                return false;
            }
        }
        UnsafeAccess.UNSAFE.putObject(buffer, offset(currentTail), e);
        tailLazySet(currentTail + 1);

        return true;
    }

    public E poll() {
        final long currentHead = getHead();
        if (currentHead >= tailCache) {
            tailCache = getTail();
            if (currentHead >= tailCache) {
                return null;
            }
        }

        final long offset = offset(currentHead);
        @SuppressWarnings("unchecked")
        final E e = (E) UnsafeAccess.UNSAFE.getObject(buffer, offset);
        UnsafeAccess.UNSAFE.putObject(buffer, offset, null);

        headLazySet(currentHead + 1);

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
        long currentHead = getHead();
        return getElement(currentHead);
    }

    @SuppressWarnings("unchecked")
    private E getElement(long index) {
        final long offset = offset(index);
        return (E) UnsafeAccess.UNSAFE.getObject(buffer, offset);
    }

    public int size() {
        return (int) (getTail() - getHead());
    }

    public boolean isEmpty() {
        return getTail() == getHead();
    }

    public boolean contains(final Object o) {
        if (null == o) {
            return false;
        }

        for (long i = getHead(), limit = getTail(); i < limit; i++) {
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
}
