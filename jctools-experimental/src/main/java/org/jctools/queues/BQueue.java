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

import org.jctools.util.Pow2;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

import static org.jctools.util.UnsafeAccess.UNSAFE;

abstract class BQueueL0Pad {
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

abstract class BQueueColdFields<E> extends BQueueL0Pad {
    protected static final int BUFFER_PAD = 32;
    protected static final long ARRAY_BASE;
    protected static final int ELEMENT_SHIFT;
    protected static final int TICKS = Integer.getInteger("spin.ticks", 200);
    static {
        final int scale = UNSAFE.arrayIndexScale(Object[].class);

        if (4 == scale) {
            ELEMENT_SHIFT = 2;
        } else if (8 == scale) {
            ELEMENT_SHIFT = 3;
        } else {
            throw new IllegalStateException("Unknown pointer size");
        }
        ARRAY_BASE = UNSAFE.arrayBaseOffset(Object[].class)
                + (BUFFER_PAD << ELEMENT_SHIFT);
    }
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 32);
    protected static final int POLL_BATCH_SIZE = Integer.getInteger("poll.batch.size", 4096);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    protected BQueueColdFields(int capacity) {
        if (Pow2.isPowerOfTwo(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = Pow2.roundToPowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        buffer = (E[]) new Object[this.capacity + BUFFER_PAD * 2];
    }
}

abstract class BQueueL1Pad<E> extends BQueueColdFields<E> {
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

    protected BQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class BQueueOfferFields<E> extends BQueueL1Pad<E> {
    protected long tail;
    protected long batchTail;

    protected BQueueOfferFields(int capacity) {
        super(capacity);
    }
}

abstract class BQueueL2Pad<E> extends BQueueOfferFields<E> {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p30, p31, p32, p33, p34, p35, p36, p37;

    public BQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class BQueuePollFields<E> extends BQueueL2Pad<E> {
    protected long head;
    protected long batchHead;
    protected int batchHistory = POLL_BATCH_SIZE;
    protected int batchSize;
    public BQueuePollFields(int capacity) {
        super(capacity);
    }
}

abstract class BQueueL3Pad<E> extends BQueuePollFields<E> {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p50, p51, p52, p53, p54, p55, p56, p57;

    protected BQueueL3Pad(int capacity) {
        super(capacity);
    }
}

public final class BQueue<E> extends BQueueL3Pad<E> implements Queue<E> {
    public BQueue(final int capacity) {
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

        if (tail >= batchTail) {
            if (null != UNSAFE.getObjectVolatile(buffer, offset(tail + OFFER_BATCH_SIZE))) {
                return false;
            }
            batchTail = tail + OFFER_BATCH_SIZE;
        }
        UNSAFE.putOrderedObject(buffer, offset(tail), e);
        tail++;

        return true;
    }
    public E poll() {
        if (head >= batchHead) {
            if (!backtrackPoll()) {
                return null;
            }
        }

        final long offset = offset(head);
        @SuppressWarnings("unchecked")
        final E e = (E) UNSAFE.getObject(buffer, offset);
        UNSAFE.putOrderedObject(buffer, offset, null);
        head++;
        return e;
    }

    boolean backtrackPoll() {
        if (batchHistory < POLL_BATCH_SIZE) {
            batchHistory = Math.min(POLL_BATCH_SIZE, batchHistory << 1);
        }
        batchSize = batchHistory;
        batchHead = head + batchSize - 1;
        while (UNSAFE.getObjectVolatile(buffer, offset(batchHead)) == null) {
            spinWait();
            if (batchSize > 1) {
                batchSize = batchSize >> 1;
                batchHead = head + batchSize - 1;
            } else {
                batchHead = head;
                return false;
            }
        }
        batchHistory = batchSize;
        return true;
    }

    private void spinWait() {
        if(TICKS == 0){
            return;
        }
	    final long deadline = System.nanoTime() + TICKS;
	    while(deadline >= System.nanoTime());
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
    	throw new UnsupportedOperationException();
    }

    public int size() {
        return (int) (tail - head);
    }

    public boolean isEmpty() {
        return tail == head;
    }

    public boolean contains(final Object o) {
    	throw new UnsupportedOperationException();
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
    	throw new UnsupportedOperationException();
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
