/*
 * Licensed under the Apache License, Version 2.0.
 */
package org.jctools.queues;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class GoldenSampleArrayQueueColdField<E> extends ConcurrentCircularArrayQueue<E>
{
    final int lookAheadStep;

    GoldenSampleArrayQueueColdField(int capacity)
    {
        super(capacity);
        lookAheadStep = capacity / 4;
    }
}

abstract class GoldenSampleArrayQueueL1Pad<E> extends GoldenSampleArrayQueueColdField<E>
{
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b

    GoldenSampleArrayQueueL1Pad(int capacity)
    {
        super(capacity);
    }
}

// $gen:ordered-fields
abstract class GoldenSampleArrayQueueProducerIndexFields<E> extends GoldenSampleArrayQueueL1Pad<E>
{
    private final static long P_INDEX_OFFSET =
            fieldOffset(GoldenSampleArrayQueueProducerIndexFields.class, "producerIndex");

    private long producerIndex;
    protected long producerLimit;

    GoldenSampleArrayQueueProducerIndexFields(int capacity)
    {
        super(capacity);
    }

    @Override
    public final long lvProducerIndex()
    {
        return UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
    }

    final long lpProducerIndex()
    {
        return producerIndex;
    }

    final void soProducerIndex(final long newValue)
    {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, newValue);
    }

    final boolean casProducerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

/**
 * Pre-existing Javadoc on the public class — the generator must prepend its NOTE without
 * dropping this content.
 */
public class GoldenSampleArrayQueue<E> extends GoldenSampleArrayQueueProducerIndexFields<E>
{
    static int sentinel;
    // Unrelated static initializer — must survive the Unsafe-cleanup pass.
    static
    {
        sentinel = 42;
    }

    public GoldenSampleArrayQueue(final int capacity)
    {
        super(capacity);
    }

    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long pIndex = this.lpProducerIndex();
        final long offset = calcCircularRefElementOffset(pIndex, mask);
        soRefElement(buffer, offset, e);
        soProducerIndex(pIndex + 1);
        return true;
    }

    @Override
    public E poll()
    {
        return null;
    }

    public final int failFastOffer(final E e)
    {
        return 0;
    }

    // $gen:ignore
    public void debugDump()
    {
    }
}
