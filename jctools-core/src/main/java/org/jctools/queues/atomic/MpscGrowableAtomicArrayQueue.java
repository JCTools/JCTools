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
package org.jctools.queues.atomic;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.QueueProgressIndicators;
import org.jctools.util.Pow2;
import org.jctools.util.RangeUtil;

import static org.jctools.queues.atomic.LinkedAtomicArrayQueueUtil.length;

import java.util.concurrent.atomic.AtomicReferenceArray;


/**
 * An MPSC array queue which starts at <i>initialCapacity</i> and grows to <i>maxCapacity</i> in linked chunks
 * of the initial size. The queue grows only when the current buffer is full and elements are not copied on
 * resize, instead a link to the new buffer is stored in the old buffer for the consumer to follow.<br>
 *
 * @param <E>
 */
public class MpscGrowableAtomicArrayQueue<E> extends MpscChunkedAtomicArrayQueue<E>
        implements MessagePassingQueue<E>, QueueProgressIndicators {

    public MpscGrowableAtomicArrayQueue(int maxCapacity) {
        super(Math.max(2, Pow2.roundToPowerOfTwo(maxCapacity / 8)), maxCapacity);
    }

    /**
     * @param initialCapacity the queue initial capacity. If chunk size is fixed this will be the chunk size.
     *        Must be 2 or more.
     * @param maxCapacity the maximum capacity will be rounded up to the closest power of 2 and will be the
     *        upper limit of number of elements in this queue. Must be 4 or more and round up to a larger
     *        power of 2 than initialCapacity.
     */
    public MpscGrowableAtomicArrayQueue(int initialCapacity, int maxCapacity) {
        super(initialCapacity, maxCapacity);
    }


    @Override
    protected int getNextBufferSize(AtomicReferenceArray<E> buffer) {
        final long maxSize = maxQueueCapacity / 2;
        RangeUtil.checkLessThanOrEqual(length(buffer), maxSize, "buffer.length");
        final int newSize = 2 * (length(buffer) - 1);
        return newSize + 1;
    }

    @Override
    protected long getCurrentBufferCapacity(long mask) {
      return (mask + 2 == maxQueueCapacity) ? maxQueueCapacity : mask;
    }
}
