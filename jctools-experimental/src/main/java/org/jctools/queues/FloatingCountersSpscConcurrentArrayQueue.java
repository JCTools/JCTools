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

import org.jctools.util.PaddedAtomicLong;
import org.jctools.util.Pow2;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

import static org.jctools.util.UnsafeAccess.UNSAFE;

abstract class FloatingCaqL0Pad {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

abstract class FloatingCaqColdFields<E> extends InlinedRingBufferL0Pad {
    protected static final int BUFFER_PAD = 32;
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 0);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;
    protected final PaddedAtomicLong tail = new PaddedAtomicLong(0);
    protected final PaddedAtomicLong head = new PaddedAtomicLong(0);

    protected final PaddedAtomicLong tailCache = new PaddedAtomicLong();
    protected final PaddedAtomicLong headCache = new PaddedAtomicLong();

    @SuppressWarnings("unchecked")
    FloatingCaqColdFields(int capacity) {
        if (Pow2.isPowerOfTwo(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = Pow2.roundToPowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        buffer = (E[]) new Object[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
    }
}

public final class FloatingCountersSpscConcurrentArrayQueue<E> extends FloatingCaqColdFields<E> implements
        Queue<E> {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
//    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
    private static final long ARRAY_BASE;
    private static final int ELEMENT_SHIFT;
    static {
        final int scale = UNSAFE.arrayIndexScale(Object[].class);

        if (4 == scale) {
            ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
        } else if (8 == scale) {
            ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
        } else {
            throw new IllegalStateException("Unknown pointer size");
        }
        ARRAY_BASE = UNSAFE.arrayBaseOffset(Object[].class)
                + (BUFFER_PAD << (ELEMENT_SHIFT - SPARSE_SHIFT));
    }

    public FloatingCountersSpscConcurrentArrayQueue(final int capacity) {
        super(capacity);
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

        final long currTail = tail.lvVal();
        final long wrapPoint = currTail - capacity + 32;
        if (headCache.lpVal() <= wrapPoint) {
            final long currHead = head.lvVal();
            headCache.spVal(currHead);
            if (currHead <= wrapPoint) {
                return false;
            }
        }
        UNSAFE.putObject(buffer, offset(currTail), e);
        tail.soVal(currTail + 1);

        return true;
    }

    public E poll() {
        final long currHead = head.lvVal();
        if (currHead >= tailCache.lpVal()) {
            final long currTail = tail.lvVal();
            tailCache.spVal(currTail);
            if (currHead >= currTail) {
                return null;
            }
        }

        final long offset = offset(currHead);
        @SuppressWarnings("unchecked")
        final E e = (E) UNSAFE.getObject(buffer, offset);
        UNSAFE.putObject(buffer, offset, null);

        head.soVal(currHead + 1);

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
        long currentHead = head.lvVal();
        return getElement(currentHead);
    }

    @SuppressWarnings("unchecked")
    private E getElement(long index) {
        final long offset = offset(index);
        return (E) UNSAFE.getObject(buffer, offset);
    }

    public int size() {
        return (int) (tail.lvVal() - head.lvVal());
    }

    public boolean isEmpty() {
        return tail.lvVal() == head.lvVal();
    }

    public boolean contains(final Object o) {
        if (null == o) {
            return false;
        }

        for (long i = head.lvVal(), limit = tail.lvVal(); i < limit; i++) {
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
