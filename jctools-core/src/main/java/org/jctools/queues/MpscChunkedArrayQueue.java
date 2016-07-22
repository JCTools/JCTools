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
    protected volatile long producerLimit;
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
            Field iField = MpscChunkedArrayQueueColdProducerFields.class.getDeclaredField("producerLimit");
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
     * @param initialCapacity the queue initial capacity. If chunk size is fixed this will be the chunk size.
     *        Must be 2 or more.
     * @param maxCapacity the maximum capacity will be rounded up to the closest power of 2 and will be the
     *        upper limit of number of elements in this queue. Must be 4 or more and round up to a larger
     *        power of 2 than initialCapacity.
     * @param fixedChunkSize if true the queue will grow in fixed sized chunks the size of initial capacity,
     *        otherwise chunk size will double on each resize until reaching the maxCapacity
     */
    public MpscChunkedArrayQueue(final int initialCapacity, int maxCapacity, boolean fixedChunkSize) {
        if (initialCapacity < 2) {
            throw new IllegalArgumentException("Initial capacity must be 2 or more");
        }
        if (maxCapacity < 4) {
            throw new IllegalArgumentException("Max capacity must be 4 or more");
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
        maxQueueCapacity = ((long)Pow2.roundToPowerOfTwo(maxCapacity)) << 1;
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
            // a successful CAS ties the ordering, lv(pIndex)-[mask/buffer]->cas(pIndex)

            // assumption behind this optimization is that queue is almost always empty or near empty
            if (producerLimit <= pIndex) {
                int result = offerSlowPath(mask, buffer, pIndex, producerLimit);
                switch (result) {
                case 0:
                    break;
                case 1:
                    continue;
                case 2:
                    return false;
                case 3:
                    resize(mask, buffer, pIndex, consumerIndex, maxQueueCapacity, e);
                    return true;
                }
            }

            if (casProducerIndex(pIndex, pIndex + 2)) {
                break;
            }
        }
        // INDEX visible before ELEMENT, consistent with consumer expectation
        final long offset = modifiedCalcElementOffset(pIndex, mask);
        soElement(buffer, offset, e);
        return true;
    }

    /**
     * We do not inline resize into this method because we do not resize on fill.
     */
    private int offerSlowPath(long mask, E[] buffer, long pIndex, long producerLimit) {
        int result;
        final long consumerIndex = lvConsumerIndex();
        final long maxQueueCapacity = this.maxQueueCapacity;
        long bufferCapacity = getCurrentBufferCapacity(mask, maxQueueCapacity);
        result = 0;// 0 - goto pIndex CAS
        if (consumerIndex + bufferCapacity > pIndex) {
            if (!casProducerLimit(producerLimit, consumerIndex + bufferCapacity)) {
                result = 1;// retry from top
            }
        }
        // full and cannot grow
        else if (consumerIndex == (pIndex - maxQueueCapacity)) {
            result = 2;// -> return false;
        }
        // grab index for resize -> set lower bit
        else if (casProducerIndex(pIndex, pIndex + 1)) {
            result = 3;// -> return true
        }
        else {
            result = 1;// failed resize attempt, retry from top
        }
        return result;
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
        if (e == null) {
            if (index != lvProducerIndex()) {
                // poll() == null iff queue is empty, null element is not strong enough indicator, so we must
                // check the producer index. If the queue is indeed not empty we spin until element is
                // visible.
                do {
                    e = lvElement(buffer, offset);
                } while (e == null);
            }
            else {
                return null;
            }
        }
        if (e == JUMP) {
            final E[] nextBuffer = getNextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, index);
        }
        soElement(buffer, offset, null);
        soConsumerIndex(index + 2);
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
        final long nextArrayOffset = nextArrayOffset(mask);
        final E[] nextBuffer = (E[]) lvElement(buffer, nextArrayOffset);
        soElement(buffer, nextArrayOffset, null);
        return nextBuffer;
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
        consumerMask = (nextBuffer.length - 2) << 1;
        final long offsetInNew = modifiedCalcElementOffset(index, consumerMask);
        return offsetInNew;
    }

    @Override
    public final int size() {
        // NOTE: because indices are on even numbers we cannot use the size util.

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
                return (int) ((currentProducerIndex - after) >> 1);
            }
        }
    }

    @Override
    public final boolean isEmpty() {
        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures this method is conservative in it's estimate. Note that as this is an MPMC there is
        // nothing we can do to make this an exact method.
        return (this.lvConsumerIndex() == this.lvProducerIndex());
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
        return producerLimit;
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
        if (e == null) {
            return null;
        }
        if (e == JUMP) {
            final E[] nextBuffer = getNextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, index);
        }
        soElement(buffer, offset, null);
        soConsumerIndex(index + 2);
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
    public int fill(Supplier<E> s, int batchSize) {
        long mask;
        E[] buffer;
        long pIndex;
        int claimedSlots;
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
            // a successful CAS ties the ordering, lv(pIndex)->[mask/buffer]->cas(pIndex)

            // we want 'limit' slots, but will settle for whatever is visible to 'producerLimit'
            long batchIndex = Math.min(producerLimit, pIndex + 2 * batchSize);

            if (pIndex == producerLimit || producerLimit < batchIndex) {
                int result = offerSlowPath(mask, buffer, pIndex, producerLimit);
                switch (result) {
                case 1:
                    continue;
                case 2:
                    return 0;
                case 3:
                    resize(mask, buffer, pIndex, consumerIndex, maxQueueCapacity, s.get());
                    return 1;
                }
            }

            // claim limit slots at once
            if (casProducerIndex(pIndex, batchIndex)) {
                claimedSlots = (int) ((batchIndex - pIndex) / 2);
                break;
            }
        }

        int i = 0;
        for (i = 0; i < claimedSlots; i++) {
            final long offset = modifiedCalcElementOffset(pIndex + 2 * i, mask);
            soElement(buffer, offset, s.get());
        }
        return claimedSlots;
    }

    private void resize(long mask, E[] buffer, long pIndex, final long consumerIndex,
            final long maxQueueCapacity, final E e) {
        int newBufferLength = getNextBufferCapacity(buffer, maxQueueCapacity);
        final E[] newBuffer = allocate(newBufferLength);

        producerBuffer = newBuffer;
        producerMask = (newBufferLength - 2) << 1;

        final long offsetInOld = modifiedCalcElementOffset(pIndex, mask);
        final long offsetInNew = modifiedCalcElementOffset(pIndex, producerMask);


        soElement(newBuffer, offsetInNew, e);// element in new array
        soElement(buffer, nextArrayOffset(mask), newBuffer);// buffer linked

        // ASSERT code
        final long available = maxQueueCapacity - (pIndex - consumerIndex);
        if (available <= 0) {
            throw new IllegalStateException();
        }

        // invalidate racing CASs
        soProducerLimit(pIndex + Math.min(mask, available));

        // make resize visible to the other producers
        soProducerIndex(pIndex + 2);

        // INDEX visible before ELEMENT, consistent with consumer expectation

        // make resize visible to consumer
        soElement(buffer, offsetInOld, JUMP);
    }

    private int getNextBufferCapacity(E[] buffer, final long maxQueueCapacity) {
        int newBufferLength = buffer.length;
        if (isFixedChunkSize) {
            newBufferLength = buffer.length;
        }
        else {
            if (buffer.length - 1 == maxQueueCapacity) {
                throw new IllegalStateException();
            }
            newBufferLength = 2 * buffer.length - 1;
        }
        return newBufferLength;
    }

    protected long getCurrentBufferCapacity(long mask, final long maxQueueCapacity) {
        // consider replacing if with subclass
        return (!isFixedChunkSize && mask + 2 == maxQueueCapacity) ? maxQueueCapacity
                : mask;
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
    public void fill(Supplier<E> s,
            WaitStrategy w,
            ExitCondition exit) {

        while (exit.keepRunning()) {
            while (fill(s, MpmcArrayQueue.RECOMENDED_OFFER_BATCH) != 0) {
                continue;
            }
            int idleCounter = 0;
            while (fill(s, MpmcArrayQueue.RECOMENDED_OFFER_BATCH) == 0 && exit.keepRunning()) {
                idleCounter = w.idle(idleCounter);
            }

        }
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
    public int drain(Consumer<E> c) {
        return drain(c, capacity());
    }

    @Override
    public int drain(final Consumer<E> c, final int limit) {
        /**
         * Impl note: there are potentially some small gains to be had by manually inlining relaxedPoll() and hoisting
         * reused fields out to reduce redundant reads.
         */
        int i = 0;
        E m;
        for (; i < limit && (m = relaxedPoll()) != null; i++) {
            c.accept(m);
        }
        return i;
    }
}
