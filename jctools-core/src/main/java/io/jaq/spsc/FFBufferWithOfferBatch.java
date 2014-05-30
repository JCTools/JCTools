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

import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.common.ConcurrentRingBuffer;
import io.jaq.util.UnsafeAccess;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

class FFBufferOfferBatchL1Pad<E> extends ConcurrentRingBuffer<E> {
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
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(FFBufferOfferBatchTailField.class
                    .getDeclaredField("tail"));
            HEAD_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(FFBufferOfferBatchHeadField.class
                    .getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);

    public FFBufferWithOfferBatch(final int capacity) {
        super(Math.max(capacity, 2 * OFFER_BATCH_SIZE));
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

    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        E[] lb = buffer;
        if (tail >= batchTail) {
            if (null != lvElement(lb, calcOffset(tail + OFFER_BATCH_SIZE))) {
                return false;
            }
            batchTail = tail + OFFER_BATCH_SIZE;
        }
        soElement(lb, calcOffset(tail), e);
        tail++;

        return true;
    }

    public E poll() {
        final long offset = calcOffset(head);
        final E[] lb = buffer;
        final E e = lvElement(lb, offset);
        if (null == e) {
            return null;
        }
        soElement(lb, offset, null);
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

    private E getElement(long index) {
        return lvElement(buffer, calcOffset(index));
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
