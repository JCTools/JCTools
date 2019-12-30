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

import java.util.Arrays;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeLongArrayAccess.*;
import static org.jctools.util.UnsafeRefArrayAccess.*;

@InternalAPI
final class MpmcUnboundedXaddChunk<E>
{
    private static final long PREV_OFFSET = fieldOffset(MpmcUnboundedXaddChunk.class, "prev");
    private static final long NEXT_OFFSET = fieldOffset(MpmcUnboundedXaddChunk.class, "next");
    private static final long INDEX_OFFSET = fieldOffset(MpmcUnboundedXaddChunk.class, "index");

    final static int CHUNK_CONSUMED = -1;

    private final E[] buffer;
    private final long[] sequence;

    private volatile MpmcUnboundedXaddChunk<E> prev;
    private volatile long index;
    private volatile MpmcUnboundedXaddChunk<E> next;

    MpmcUnboundedXaddChunk(long index, MpmcUnboundedXaddChunk<E> prev, int size, boolean pooled)
    {
        buffer = allocateRefArray(size);
        // next is null
        soPrev(prev);
        spIndex(index);
        if (pooled)
        {
            sequence = allocateLongArray(size);
            Arrays.fill(sequence, MpmcUnboundedXaddChunk.CHUNK_CONSUMED);
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

    void soSequence(int index, long e)
    {
        soLongElement(sequence, calcLongElementOffset(index), e);
    }

    long lvSequence(int index)
    {
        return lvLongElement(sequence, calcLongElementOffset(index));
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

    void soPrev(MpmcUnboundedXaddChunk<E> value)
    {
        UNSAFE.putObject(this, PREV_OFFSET, value);
    }

    void soElement(int index, E e)
    {
        soRefElement(buffer, calcRefElementOffset(index), e);
    }

    E lvElement(int index)
    {
        return lvRefElement(buffer, calcRefElementOffset(index));
    }
}
