package org.jctools.queues;

import org.jctools.util.InternalAPI;
import org.jctools.util.UnsafeRefArrayAccess;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

@InternalAPI
final class MpscUnboundedXaddChunk<E>
{
    private static final long PREV_OFFSET = fieldOffset(MpscUnboundedXaddChunk.class, "prev");
    private static final long NEXT_OFFSET = fieldOffset(MpscUnboundedXaddChunk.class, "next");
    private static final long INDEX_OFFSET = fieldOffset(MpscUnboundedXaddChunk.class, "index");

    final static int NIL_CHUNK_INDEX = -1;

    private final E[] buffer;
    private final boolean pooled;

    private volatile MpscUnboundedXaddChunk<E> prev;
    private volatile long index;
    private volatile MpscUnboundedXaddChunk<E> next;

    MpscUnboundedXaddChunk(long index, MpscUnboundedXaddChunk<E> prev, int size, boolean pooled)
    {
        buffer = allocate(size);
        // next is null
        spPrev(prev);
        spIndex(index);
        this.pooled = pooled;
    }

    MpscUnboundedXaddChunk<E> lvNext()
    {
        return next;
    }

    MpscUnboundedXaddChunk<E> lpPrev()
    {
        return (MpscUnboundedXaddChunk<E>) UNSAFE.getObject(this, PREV_OFFSET);
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

    void soNext(MpscUnboundedXaddChunk<E> value)
    {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, value);
    }

    void spPrev(MpscUnboundedXaddChunk<E> value)
    {
        UNSAFE.putObject(this, PREV_OFFSET, value);
    }

    void soElement(int index, E e)
    {
        UnsafeRefArrayAccess.soElement(buffer, UnsafeRefArrayAccess.calcElementOffset(index), e);
    }

    E lvElement(int index)
    {
        return UnsafeRefArrayAccess.lvElement(buffer, UnsafeRefArrayAccess.calcElementOffset(index));
    }

    boolean isPooled()
    {
        return pooled;
    }
}
