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
package io.jaq.spsc;

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

class FFBufferOfferBatchL0Pad {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

class FFBufferOfferBatchColdFields<E> extends FFBufferOfferBatchL0Pad {
    protected static final int BUFFER_PAD = 32;
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 2);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    public FFBufferOfferBatchColdFields(int capacity) {
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

class FFBufferOfferBatchL1Pad<E> extends FFBufferOfferBatchColdFields<E> {
    long p10, p11, p12, p13, p14, p15, p16;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public FFBufferOfferBatchL1Pad(int capacity) {
        super(capacity);
    }
}

class FFBufferOfferBatchTailField<E> extends FFBufferOfferBatchL1Pad<E> {
    protected long tail;
    protected long batchTail;

    public FFBufferOfferBatchTailField(int capacity) {
        super(capacity);
    }
}

class FFBufferOfferBatchL2Pad<E> extends FFBufferOfferBatchTailField<E> {
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public FFBufferOfferBatchL2Pad(int capacity) {
        super(capacity);
    }
}

class FFBufferOfferBatchHeadField<E> extends FFBufferOfferBatchL2Pad<E> {
    protected long head;

    public FFBufferOfferBatchHeadField(int capacity) {
        super(capacity);
    }
}

class FFBufferOfferBatchL3Pad<E> extends FFBufferOfferBatchHeadField<E> {
    long p40, p41, p42, p43, p44, p45, p46;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public FFBufferOfferBatchL3Pad(int capacity) {
        super(capacity);
    }
}

public final class FFBufferWithOfferBatch<E> extends FFBufferOfferBatchL3Pad<E> implements Queue<E>,
        ConcurrentQueue<E>, ConcurrentQueueProducer<E>, ConcurrentQueueConsumer<E> {
    private final static long TAIL_OFFSET;
    private final static long HEAD_OFFSET;
    private static final long ARRAY_BASE;
    private static final int ELEMENT_SHIFT;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(FFBufferOfferBatchTailField.class
                    .getDeclaredField("tail"));
            HEAD_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(FFBufferOfferBatchHeadField.class
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
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);

    public FFBufferWithOfferBatch(final int capacity) {
        super(Math.max(capacity,2*OFFER_BATCH_SIZE));
    }

    private long getHeadV() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET);
    }

    private long getTailV() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET);
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

        E[] lb = buffer;
        if (tail >= batchTail) {
            if (null != UNSAFE.getObjectVolatile(lb, offset(tail + OFFER_BATCH_SIZE))) {
                return false;
            }
            batchTail = tail + OFFER_BATCH_SIZE;
        }
        UNSAFE.putOrderedObject(lb, offset(tail), e);
        tail++;

        return true;
    }

    public E poll() {
        final long offset = offset(head);
        E[] lb = buffer;
        @SuppressWarnings("unchecked")
        final E e = (E) UnsafeAccess.UNSAFE.getObjectVolatile(lb, offset);
        if (null == e) {
            return null;
        }
        UnsafeAccess.UNSAFE.putOrderedObject(lb, offset, null);
        head++;
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
        return getElement(head);
    }

    @SuppressWarnings("unchecked")
    private E getElement(long index) {
        return (E) UnsafeAccess.UNSAFE.getObject(buffer, offset(index));
    }

    public int size() {
        // TODO: this is ugly :( the head/tail cannot be counted on to be written out, so must take max
        return (int) (Math.max(getTailV(), tail) - Math.max(getHeadV(), head));
    }

    public boolean isEmpty() {
        // TODO: a better indication from consumer is peek() == null, or from producer get(head-1) == null
        return size() == 0;
    }

    @Override
    public int capacity() {
        return capacity - OFFER_BATCH_SIZE;
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
        while (null != poll())
            ;
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        // TODO: potential for improved layout for consumer instance with all require fields packed.
        return this;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        // TODO: potential for improved layout for producer instance with all require fields packed.
        return this;
    }
}
