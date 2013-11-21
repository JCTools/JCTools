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
import io.jaq.util.Pow2;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

abstract class BQueueL0Pad {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p10, p11, p12, p13, p14, p15, p16, p17;
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
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 512);
    protected static final int POLL_MAX_BATCH = Integer.getInteger("poll.batch.size", 32);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    protected BQueueColdFields(int capacity) {
        if (Pow2.isPowerOf2(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = Pow2.findNextPositivePowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        buffer = (E[]) new Object[this.capacity + BUFFER_PAD * 2];
    }
}

abstract class BQueueL1Pad<E> extends BQueueColdFields<E> {
    protected long p00, p01, p02, p03, p04, p05, p06, p07;
    protected long p10, p11, p12, p13, p14, p15, p16, p17;

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
    protected int batchHistory = POLL_MAX_BATCH;
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
        if (batchHistory < POLL_MAX_BATCH) {
            batchHistory = Math.min(POLL_MAX_BATCH, batchHistory << 1);
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
