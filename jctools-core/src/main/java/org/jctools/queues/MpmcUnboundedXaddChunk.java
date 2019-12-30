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

import static org.jctools.util.UnsafeLongArrayAccess.*;

@InternalAPI
final class MpmcUnboundedXaddChunk<E> extends MpUnboundedXaddChunk<MpmcUnboundedXaddChunk<E>, E>
{
    private final long[] sequence;

    MpmcUnboundedXaddChunk(long index, MpmcUnboundedXaddChunk<E> prev, int size, boolean pooled)
    {
        super(index, prev, size, pooled);
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

    void soSequence(int index, long e)
    {
        soLongElement(sequence, calcLongElementOffset(index), e);
    }

    long lvSequence(int index)
    {
        return lvLongElement(sequence, calcLongElementOffset(index));
    }
}
