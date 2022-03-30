package org.jctools.queues;

import org.jctools.util.InternalAPI;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

@InternalAPI
public class MpUnboundedXaddChunk<R,E>
{
    public final static int NOT_USED = -1;

    private static final long PREV_OFFSET = fieldOffset(MpUnboundedXaddChunk.class, "prev");
    private static final long NEXT_OFFSET = fieldOffset(MpUnboundedXaddChunk.class, "next");
    private static final long INDEX_OFFSET = fieldOffset(MpUnboundedXaddChunk.class, "index");

    private final boolean pooled;
    private final E[] buffer;

    private volatile R prev;
    private volatile long index;
    private volatile R next;
    protected MpUnboundedXaddChunk(long index, R prev, int size, boolean pooled)
    {
        buffer = allocateRefArray(size);
        // next is null
        soPrev(prev);
        spIndex(index);
        this.pooled = pooled;
    }

    public final boolean isPooled()
    {
        return pooled;
    }

    public final long lvIndex()
    {
        return index;
    }

    public final void soIndex(long index)
    {
        UNSAFE.putOrderedLong(this, INDEX_OFFSET, index);
    }

    final void spIndex(long index)
    {
        UNSAFE.putLong(this, INDEX_OFFSET, index);
    }

    public final R lvNext()
    {
        return next;
    }

    public final void soNext(R value)
    {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, value);
    }

    public final R lvPrev()
    {
        return prev;
    }

    public final void soPrev(R value)
    {
        UNSAFE.putObject(this, PREV_OFFSET, value);
    }

    public final void soElement(int index, E e)
    {
        soRefElement(buffer, calcRefElementOffset(index), e);
    }

    public final E lvElement(int index)
    {
        return lvRefElement(buffer, calcRefElementOffset(index));
    }

    public final E spinForElement(int index, boolean isNull)
    {
        E[] buffer = this.buffer;
        long offset = calcRefElementOffset(index);
        E e;
        do
        {
            e = lvRefElement(buffer, offset);
        }
        while (isNull != (e == null));
        return e;
    }
}
