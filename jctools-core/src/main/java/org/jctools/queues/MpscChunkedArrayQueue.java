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

import java.lang.reflect.Field;
import java.util.AbstractQueue;
import java.util.Iterator;

import org.jctools.util.Pow2;

abstract class MpscChunkedArrayQueuePad1<E> extends AbstractQueue<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscChunkedArrayQueueProducerFields<E> extends MpscChunkedArrayQueuePad1<E> {
    protected long producerIndex;
}

abstract class MpscChunkedArrayQueuePad2<E> extends MpscChunkedArrayQueueProducerFields<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscChunkedArrayQueueColdProducerFields<E> extends MpscChunkedArrayQueuePad2<E> {
    protected long maxQueueCapacity;
    protected long producerMask;
    protected E[] producerBuffer;
    protected volatile long prodcuerLimit;
    protected boolean isFixedChunkSize = false;
}

abstract class MpscChunkedArrayQueuePad3<E> extends MpscChunkedArrayQueueColdProducerFields<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscChunkedArrayQueueConsumerFields<E> extends MpscChunkedArrayQueuePad3<E> {
    protected long consumerMask;
    protected E[] consumerBuffer;
    protected long consumerIndex;
}

/**
 * An MPSC array queue which starts at <i>initialCapacity</i> and grows to <i>maxCapacity</i> in linked chunks
 * of the initial size. The queue grows only when the current buffer is full and elements are not copied on
 * resize, instead a link to the new buffer is stored in the old buffer for the consumer to follow.<br>
 *
 *
 * @param <E>
 */
public class MpscChunkedArrayQueue<E> extends MpscChunkedArrayQueueConsumerFields<E>
        implements MessagePassingQueue<E>, QueueProgressIndicators {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    private final static long P_INDEX_OFFSET;
    private final static long C_INDEX_OFFSET;
    private final static long P_LIMIT_OFFSET;

    static {
        try {
            Field iField = MpscChunkedArrayQueueProducerFields.class.getDeclaredField("producerIndex");
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = MpscChunkedArrayQueueConsumerFields.class.getDeclaredField("consumerIndex");
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = MpscChunkedArrayQueueColdProducerFields.class.getDeclaredField("prodcuerLimit");
            P_LIMIT_OFFSET = UNSAFE.objectFieldOffset(iField);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private final static Object JUMP = new Object();

    public MpscChunkedArrayQueue(final int maxCapacity) {
        this(Math.max(2, Pow2.roundToPowerOfTwo(maxCapacity / 8)), maxCapacity, false);
    }

    /**
     * @param initialCapacity
     *            the queue initial capacity. If chunk size is fixed this will be the chunk size. Must be 2 or
     *            more.
     * @param maxCapacity
     *            the maximum capacity will be rounded up to the closest power of 2 and will be the upper
     *            limit of number of elements in this queue. Must be 4 or more and round up to a larger power
     *            of 2 than initialCapacity.
     * @param fixedChunkSize
     *            if true the queue will grow in fixed sized chunks the size of initial capacity, otherwise
     *            chunk size will double on each resize until reaching the maxCapacity
     */
    public MpscChunkedArrayQueue(final int initialCapacity, int maxCapacity, boolean fixedChunkSize) {
        if (initialCapacity < 2) {
            throw new IllegalArgumentException("Initial capacity must be 2 or more");
        }
        if (maxCapacity < 4) {
            throw new IllegalArgumentException("Initial capacity must be 4 or more");
        }
        if (Pow2.roundToPowerOfTwo(initialCapacity) >= Pow2.roundToPowerOfTwo(maxCapacity)) {
            throw new IllegalArgumentException(
                    "Initial capacity cannot exceed maximum capacity(both rounded up to a power of 2)");
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
        isFixedChunkSize = fixedChunkSize;
        soProducerLimit(mask); // we know it's all empty to start with
    }

    @Override
    public final Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }

        long mask;
        E[] buffer;
        long pIndex;

        while (true) {
            long producerLimit = lvProducerLimit();
            pIndex = lvProducerIndex();
            // lower bit is indicative of resize, if we see it we spin until it's cleared
            if ((pIndex & 1) == 1) {
                continue;
            }
            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1)

            // mask/buffer may get changed by resizing -> only use for array access after successful CAS.
            mask = this.producerMask;
            buffer = this.producerBuffer;
            // by loading the fields between the pIndex load and CAS we guarantee the index and the fields
            // 'match'

            // assumption behind this optimization is that queue is almost always empty or near empty
            if (producerLimit <= pIndex) {
                final long consumerIndex = lvConsumerIndex();
                final long maxQueueCapacity = this.maxQueueCapacity;
                long bufferCapacity = (!isFixedChunkSize && mask + 2 == maxQueueCapacity) ? maxQueueCapacity
                        : mask;

                if (consumerIndex + bufferCapacity > pIndex) {
                    if (!casProducerLimit(producerLimit, consumerIndex + bufferCapacity)) {
                        continue; // 1 - skip CAS, no point
                    }
                    // 0 - goto pIndex CAS
                }
                // full and cannot grow
                else if (consumerIndex == (pIndex - maxQueueCapacity)) {
                    return false; // 2
                }
                // grab index for resize -> set lower bit
                else if (casProducerIndex(pIndex, pIndex + 1)) {
                    // resize will adjust the consumerIndexCache
                    resize(pIndex, buffer, mask, e, consumerIndex);
                    return true; // 3
                }
                else {
                    continue; // 1 - skip CAS, no point
                }
            }

            if (casProducerIndex(pIndex, pIndex + 2)) {
                break;
            }
        }

        final long offset = modifiedCalcElementOffset(pIndex, mask);
        soElement(buffer, offset, e);
        return true;
    }

    private void resize(long pIndex, E[] oldBuffer, long oldMask, E e, long cIndex) {
        int newBufferLength = oldBuffer.length;
        if (isFixedChunkSize) {
            newBufferLength = oldBuffer.length;
        }
        else {
            if (oldBuffer.length - 1 == maxQueueCapacity) {
                throw new IllegalStateException();
            }
            newBufferLength = 2 * oldBuffer.length - 1;
        }

        final E[] newBuffer = allocate(newBufferLength);

        producerBuffer = newBuffer;
        producerMask = (newBufferLength - 2) << 1;

        final long offsetInOld = modifiedCalcElementOffset(pIndex, oldMask);
        final long offsetInNew = modifiedCalcElementOffset(pIndex, producerMask);
        soElement(newBuffer, offsetInNew, e);
        soElement(oldBuffer, nextArrayOffset(oldMask), newBuffer);
        final long available = maxQueueCapacity - (pIndex - cIndex);
        if (available <= 0) {
            throw new IllegalStateException();
        }
        // invalidate racing CASs
        soProducerLimit(pIndex + Math.min(oldMask, available));

        // make resize visible to consumer
        soElement(oldBuffer, offsetInOld, JUMP);

        // make resize visible to the other producers
        soProducerIndex(pIndex + 2);
    }

    /**
     * This method assumes index is actually (index << 1) because lower bit is used for resize. This is
     * compensated for by reducing the element shift. The computation is constant folded, so there's no cost.
     */
    private static long modifiedCalcElementOffset(long index, long mask) {
        return REF_ARRAY_BASE + ((index & mask) << (REF_ELEMENT_SHIFT - 1));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E poll() {
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long mask = consumerMask;

        final long offset = modifiedCalcElementOffset(index, mask);
        Object e = lvElement(buffer, offset);// LoadLoad
        if (e == null && index != lvProducerIndex()) {
            // poll() == null iff queue is empty, null element is not strong enough indicator, so we must
            // check the producer index. If the queue is indeed not empty we spin until element is visible.
            while ((e = lvElement(buffer, offset)) == null)
                ;
        }
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
    public E peek() {
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long mask = consumerMask;

        final long offset = modifiedCalcElementOffset(index, mask);
        Object e = lvElement(buffer, offset);// LoadLoad
        if (e == null && index != lvProducerIndex()) {
            // peek() == null iff queue is empty, null element is not strong enough indicator, so we must
            // check the producer index. If the queue is indeed not empty we spin until element is visible.
            while ((e = lvElement(buffer, offset)) == null)
                ;
        }
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
        return modifiedCalcElementOffset(mask + 2, Long.MAX_VALUE);
    }

    private E newBufferPoll(E[] nextBuffer, final long index) {
        final long offsetInNew = newBufferAndOffset(nextBuffer, index);
        final E n = lvElement(nextBuffer, offsetInNew);// LoadLoad
        if (n == null) {
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
        // mask doesn't change
        consumerMask = (nextBuffer.length - 2) << 1;
        final long offsetInNew = modifiedCalcElementOffset(index, consumerMask);
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

    private long lvProducerLimit() {
        return prodcuerLimit;
    }

    private boolean casProducerLimit(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, P_LIMIT_OFFSET, expect, newValue);
    }

    private void soProducerLimit(long v) {
        UNSAFE.putOrderedLong(this, P_LIMIT_OFFSET, v);
    }

    @Override
    public long currentProducerIndex() {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex() {
        return lvConsumerIndex();
    }

    @Override
    public int capacity() {
        return (int) (maxQueueCapacity / 2);
    }

    @Override
    public boolean relaxedOffer(E e) {
        return offer(e);
    }

    @SuppressWarnings("unchecked")
    @Override
    public E relaxedPoll() {
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long mask = consumerMask;

        final long offset = modifiedCalcElementOffset(index, mask);
        Object e = lvElement(buffer, offset);// LoadLoad
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

    @SuppressWarnings("unchecked")
    @Override
    public E relaxedPeek() {
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long mask = consumerMask;

        final long offset = modifiedCalcElementOffset(index, mask);
        Object e = lvElement(buffer, offset);// LoadLoad

        if (e == JUMP) {
            return newBufferPeek(getNextBuffer(buffer, mask), index);
        }
        return (E) e;
    }

    @Override
    public int drain(Consumer<E> c) {
        return drain(c, capacity());
    }

    @Override
    public int fill(Supplier<E> s) {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        final int capacity = capacity();
        do {
            final int filled = fill(s, MpmcArrayQueue.RECOMENDED_OFFER_BATCH);
            if (filled == 0) {
                return (int) result;
            }
            result += filled;
        } while (result <= capacity);
        return (int) result;
    }

    @Override
    public int drain(final Consumer<E> c, final int limit) {
        int i = 0;
        E m;
        for (; i < limit && (m = relaxedPoll()) != null; i++) {
            c.accept(m);
        }
        return i;
    }

    @Override
    public int fill(Supplier<E> s, int limit) {
        int i = 0;
        // BUG: if queue is full, will take from supplier and drop on the floor
        for (i = 0; i < limit && relaxedOffer(s.get()); i++)
            ;
        return i;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy w, ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            E e = relaxedPoll();
            if (e == null) {
                idleCounter = w.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
            c.accept(e);
        }
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy w, ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            E e = s.get();
            while (!relaxedOffer(e)) {
                idleCounter = w.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
        }
    }
}
