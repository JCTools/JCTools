package org.jctools.queues;

import java.lang.reflect.Field;
import java.util.AbstractQueue;
import java.util.Iterator;

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;

import static org.jctools.queues.CircularArrayOffsetCalculator.calcElementOffset;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeRefArrayAccess.REF_ARRAY_BASE;
import static org.jctools.util.UnsafeRefArrayAccess.REF_ELEMENT_SHIFT;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

abstract class BaseSpscLinkedArrayQueuePrePad<E> extends AbstractQueue<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12;
    // p13, p14, p15, p16, p17; drop 4 longs, the cold fields act as buffer
}
abstract class BaseSpscLinkedArrayQueueProducerColdFields<E> extends BaseSpscLinkedArrayQueuePrePad<E> {
    protected int maxQueueCapacity; // ignored by the unbounded implementation
    protected long producerQueueLimit;// ignored by the unbounded implementation
    protected long producerBufferLimit;
    protected long producerMask; // fixed for chunked and unbounded
    protected E[] producerBuffer;
}
abstract class BaseSpscLinkedArrayQueueProducerFields<E> extends BaseSpscLinkedArrayQueueProducerColdFields<E> {
    protected long producerIndex;
}
abstract class BaseSpscLinkedArrayQueueL2Pad<E> extends BaseSpscLinkedArrayQueueProducerFields<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}
abstract class BaseSpscLinkedArrayQueueConsumerColdFields<E> extends BaseSpscLinkedArrayQueueL2Pad<E> {
    protected long consumerMask;
    protected E[] consumerBuffer;
}
abstract class BaseSpscLinkedArrayQueueConsumerField<E> extends BaseSpscLinkedArrayQueueConsumerColdFields<E> {
    protected long consumerIndex;
}

abstract class BaseSpscLinkedArrayQueue<E> extends BaseSpscLinkedArrayQueueConsumerField<E>
        implements QueueProgressIndicators, IndexedQueue {

    protected static final Object JUMP = new Object();

    private final static long P_INDEX_OFFSET;
    private final static long C_INDEX_OFFSET;
    static {
        try {
            Field iField = BaseSpscLinkedArrayQueueProducerFields.class.getDeclaredField("producerIndex");
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = BaseSpscLinkedArrayQueueConsumerField.class.getDeclaredField("consumerIndex");
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    protected final void soProducerIndex(long v) {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, v);
    }

    protected final void soConsumerIndex(long v) {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, v);
    }

    public final long lvProducerIndex() {
        return UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
    }

    public final long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }

    @Override
    public final Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long currentProducerIndex() {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex() {
        return lvConsumerIndex();
    }


    protected final void soNext(E[] curr, E[] next) {
        soElement(curr, nextArrayOffset(curr), next);
    }

    @SuppressWarnings("unchecked")
    protected final E[] lvNext(E[] curr) {
        final long nextArrayOffset = nextArrayOffset(curr);
        final E[] nextBuffer = (E[]) lvElement(curr, nextArrayOffset);
        soElement(curr, nextArrayOffset, null);
        return nextBuffer;
    }

    private long nextArrayOffset(E[] curr) {
        return REF_ARRAY_BASE + ((long) (curr.length - 1) << REF_ELEMENT_SHIFT);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = producerBuffer;
        final long index = producerIndex;
        final long mask = producerMask;
        final long offset = calcElementOffset(index, mask);
        // expected hot path
        if (index < producerBufferLimit) {
            writeToQueue(buffer, e, index, offset);
            return true;
        }
        return offerColdPath(buffer, mask, e, index, offset);
    }


    protected boolean offerColdPath(final E[] buffer, final long mask, final E e, final long pIndex,
            final long offset) {
        // use a fixed lookahead step based on buffer capacity
        final long lookAheadStep = (mask + 1) / 4;
        long pBufferLimit = pIndex + lookAheadStep;

        // Sadly I see no good way to express the control flow below using inheritance.
        if (isBounded()) {
            long pQueueLimit = producerQueueLimit;

            if (pIndex >= pQueueLimit) {
                // we tested against a potentially out of date queue limit, refresh it
                long cIndex = lvConsumerIndex();
                producerQueueLimit = pQueueLimit = cIndex + maxQueueCapacity;
                // if we're full we're full
                if (pIndex >= pQueueLimit) {
                    return false;
                }
            }
            // if buffer limit is after queue limit we use queue limit. We need to handle overflow so
            // cannot use Math.min
            if (pBufferLimit - pQueueLimit > 0) {
                pBufferLimit = pQueueLimit;
            }
        }

        // go around the buffer or add a new buffer
        if (pBufferLimit > pIndex + 1 && // there's sufficient room in buffer/queue to use pBufferLimit
            null == lvElement(buffer, calcElementOffset(pBufferLimit, mask)))
        {
            producerBufferLimit = pBufferLimit - 1; // joy, there's plenty of room
            writeToQueue(buffer, e, pIndex, offset);
        } else if (null == lvElement(buffer, calcElementOffset(pIndex + 1, mask))) { // buffer is not full
            writeToQueue(buffer, e, pIndex, offset);
        }
        else {
            // we got one slot left to write into, and we are not full. Need to link new buffer.
            linkNewBuffer(buffer, pIndex, offset, e, mask);
        }
        return true;
    }

    protected abstract boolean isBounded();

    protected abstract void linkNewBuffer(E[] buffer, long pIndex, long offset, E e, long mask);

    protected final void linkOldToNew(final long currIndex, final E[] oldBuffer, final long offset,
            final E[] newBuffer, final long offsetInNew, final E e) {
        soElement(newBuffer, offsetInNew, e);// StoreStore
        // link to next buffer and add next indicator as element of old buffer
        soNext(oldBuffer, newBuffer);
        soElement(oldBuffer, offset, JUMP);
        // index is visible after elements (isEmpty/poll ordering)
        soProducerIndex(currIndex + 1);// this ensures atomic write of long on 32bit platforms
    }

    protected final void writeToQueue(final E[] buffer, final E e, final long index, final long offset) {
        soElement(buffer, offset, e);// StoreStore
        soProducerIndex(index + 1);// this ensures atomic write of long on 32bit platforms
    }
    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E poll() {
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = consumerBuffer;
        final long index = consumerIndex;
        final long mask = consumerMask;
        final long offset = calcElementOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        boolean isNextBuffer = e == JUMP;
        if (null != e && !isNextBuffer) {
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soElement(buffer, offset, null);
            return (E) e;
        } else if (isNextBuffer) {
            return newBufferPoll(buffer, index);
        }

        return null;
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
        final long offset = calcElementOffset(index, mask);
        final Object e = lvElement(buffer, offset);// LoadLoad
        if (e == JUMP) {
            return newBufferPeek(buffer, index);
        }

        return (E) e;
    }

    private E newBufferPeek(E[] buffer, final long index) {
        E[] nextBuffer = lvNext(buffer);
        consumerBuffer = nextBuffer;
        final long newMask = nextBuffer.length - 2;
        consumerMask = newMask;
        final long offsetInNew = calcElementOffset(index, newMask);
        return lvElement(nextBuffer, offsetInNew);// LoadLoad
    }

    private E newBufferPoll(E[] buffer, final long index) {
        E[] nextBuffer = lvNext(buffer);
        consumerBuffer = nextBuffer;
        final long newMask = nextBuffer.length - 2;
        consumerMask = newMask;
        final long offsetInNew = calcElementOffset(index, newMask);
        final E n = lvElement(nextBuffer, offsetInNew);// LoadLoad
        if (null == n) {
            throw new IllegalStateException("new buffer must have at least one element");
        } else {
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soElement(nextBuffer, offsetInNew, null);// StoreStore
            return n;
        }
    }

    @Override
    public final int size() {
        return IndexedQueueSizeUtil.size(this);
    }

    @Override
    public final boolean isEmpty() {
        return IndexedQueueSizeUtil.isEmpty(this);
    }
}
