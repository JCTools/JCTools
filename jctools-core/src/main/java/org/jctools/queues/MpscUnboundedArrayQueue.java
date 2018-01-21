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

import org.jctools.util.PortableJvmInfo;

import static org.jctools.queues.LinkedArrayQueueUtil.length;

/**
 * An MPSC array queue which starts at <i>initialCapacity</i> and grows indefinitely in linked chunks of the initial size.
 * The queue grows only when the current chunk is full and elements are not copied on
 * resize, instead a link to the new chunk is stored in the old chunk for the consumer to follow.<br>
 *
 * @param <E>
 */
public class MpscUnboundedArrayQueue<E> extends BaseMpscLinkedArrayQueue<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public MpscUnboundedArrayQueue(int chunkSize)
    {
        super(chunkSize);
    }


    @Override
    protected long availableInQueue(long pIndex, long cIndex)
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public int capacity()
    {
        return MessagePassingQueue.UNBOUNDED_CAPACITY;
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return drain(c, 4096);
    }

    @Override
    public int fill(Supplier<E> s)
    {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        final int capacity = 4096;
        do
        {
            final int filled = fill(s, PortableJvmInfo.RECOMENDED_OFFER_BATCH);
            if (filled == 0)
            {
                return (int) result;
            }
            result += filled;
        }
        while (result <= capacity);
        return (int) result;
    }

    @Override
    protected int getNextBufferSize(E[] buffer)
    {
        return length(buffer);
    }

    @Override
    protected long getCurrentBufferCapacity(long mask)
    {
        return mask;
    }
}
