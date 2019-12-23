package org.jctools.queues;

import org.jctools.util.InternalAPI;
import org.jctools.util.UnsafeRefArrayAccess;

import java.util.Arrays;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeLongArrayAccess.*;
import static org.jctools.util.UnsafeRefArrayAccess.allocateRefArray;

@InternalAPI
final class MpmcUnboundedXaddChunk<E>
{
    private static final long PREV_OFFSET = fieldOffset(MpmcUnboundedXaddChunk.class, "prev");
    private static final long NEXT_OFFSET = fieldOffset(MpmcUnboundedXaddChunk.class, "next");
    private static final long INDEX_OFFSET = fieldOffset(MpmcUnboundedXaddChunk.class, "index");

    final static int NIL_CHUNK_INDEX = -1;

    private final E[] buffer;
    private final long[] sequence;

    private volatile MpmcUnboundedXaddChunk<E> prev;
    private volatile long index;
    private volatile MpmcUnboundedXaddChunk<E> next;

    MpmcUnboundedXaddChunk(long index, MpmcUnboundedXaddChunk<E> prev, int size, boolean pooled)
    {
        buffer = allocateRefArray(size);
        // next is null
        spPrev(prev);
        spIndex(index);
        if (pooled)
        {
            sequence = allocateLongArray(size);
            Arrays.fill(sequence, MpmcUnboundedXaddChunk.NIL_CHUNK_INDEX);
        }
        else
        {
            sequence = null;
        }
    }

    boolean isPooled()
    {
        return sequence != null;
    }

    private static long calcSequenceOffset(long index)
    {
        return calcLongElementOffset(index);
    }

    void soSequence(int index, long e)
    {
        soLongElement(sequence, calcSequenceOffset(index), e);
    }

    long lvSequence(int index)
    {
        return lvLongElement(sequence, calcSequenceOffset(index));
    }

    MpmcUnboundedXaddChunk<E> lvNext()
    {
        return next;
    }

    MpmcUnboundedXaddChunk<E> lpPrev()
    {
        return (MpmcUnboundedXaddChunk<E>) UNSAFE.getObject(this, PREV_OFFSET);
    }

    long lvIndex()
    {
        return index;
    }

    void soIndex(long index)
    {
        UNSAFE.putOrderedLong(this, INDEX_OFFSET, index);
    }

    void spIndex(long index)
    {
        UNSAFE.putLong(this, INDEX_OFFSET, index);
    }

    void soNext(MpmcUnboundedXaddChunk<E> value)
    {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, value);
    }

    void spPrev(MpmcUnboundedXaddChunk<E> value)
    {
        UNSAFE.putObject(this, PREV_OFFSET, value);
    }

    void soElement(int index, E e)
    {
        UnsafeRefArrayAccess.soRefElement(buffer, UnsafeRefArrayAccess.calcRefElementOffset(index), e);
    }

    E lvElement(int index)
    {
        return UnsafeRefArrayAccess.lvRefElement(buffer, UnsafeRefArrayAccess.calcRefElementOffset(index));
    }
}
