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

import org.jctools.util.InternalAPI;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

@InternalAPI
final class MpscUnboundedXaddChunk<E>
{
    private static final long PREV_OFFSET = fieldOffset(MpscUnboundedXaddChunk.class, "prev");
    private static final long NEXT_OFFSET = fieldOffset(MpscUnboundedXaddChunk.class, "next");
    private static final long INDEX_OFFSET = fieldOffset(MpscUnboundedXaddChunk.class, "index");

    final static long CHUNK_CONSUMED = -1;

    private final E[] buffer;
    private final boolean pooled;

    // index is a positive monotonic number, unless set to CHUNK_CONSUMED
    private volatile long index;

    private volatile MpscUnboundedXaddChunk<E> prev;
    private volatile MpscUnboundedXaddChunk<E> next;

    MpscUnboundedXaddChunk(long index, MpscUnboundedXaddChunk<E> prev, int size, boolean pooled)
    {
        buffer = allocateRefArray(size);
        // next is null
        spPrev(prev);
        spIndex(index);
        this.pooled = pooled;
    }

    MpscUnboundedXaddChunk<E> lvNext()
    {
        return next;
    }

    void soNext(MpscUnboundedXaddChunk<E> value)
    {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, value);
    }

    MpscUnboundedXaddChunk<E> lpPrev()
    {
        return (MpscUnboundedXaddChunk<E>) UNSAFE.getObject(this, PREV_OFFSET);
    }

    void spPrev(MpscUnboundedXaddChunk<E> value)
    {
        UNSAFE.putObject(this, PREV_OFFSET, value);
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

    void soElement(int index, E e)
    {
        soRefElement(buffer, calcRefElementOffset(index), e);
    }

    E lvElement(int index)
    {
        return lvRefElement(buffer, calcRefElementOffset(index));
    }

    boolean isPooled()
    {
        return pooled;
    }
}
