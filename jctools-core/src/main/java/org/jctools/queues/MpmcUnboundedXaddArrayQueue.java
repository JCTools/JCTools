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

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;
import org.jctools.util.UnsafeRefArrayAccess;

import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Iterator;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;


abstract class MpmcProgressiveChunkedQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    static final class AtomicChunk<E>
    {
        private static final long ARRAY_BASE;
        private static final int ELEMENT_SHIFT;

        static
        {
            final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(long[].class);
            if (8 == scale)
            {
                ELEMENT_SHIFT = 3;
            }
            else
            {
                throw new IllegalStateException("Unexpected long[] element size");
            }
            // Including the buffer pad in the array base offset
            ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(long[].class);
        }

        final static int NIL_CHUNK_INDEX = -1;
        private static final long PREV_OFFSET = fieldOffset(AtomicChunk.class, "prev");
        private static final long NEXT_OFFSET = fieldOffset(AtomicChunk.class, "next");
        private static final long INDEX_OFFSET = fieldOffset(AtomicChunk.class, "index");
        private volatile AtomicChunk<E> prev;
        private volatile long index;
        private volatile AtomicChunk<E> next;
        private final long[] sequence;
        private final E[] buffer;
        private final int startIndex;

        AtomicChunk(long index, AtomicChunk<E> prev, E[] buffer, long[] sequence, int startIndex)
        {
            this.buffer = buffer;
            this.startIndex = startIndex;
            spNext(null);
            spPrev(prev);
            spIndex(index);
            this.sequence = sequence;
        }

        AtomicChunk(long index, AtomicChunk<E> prev, int size)
        {
            buffer = CircularArrayOffsetCalculator.allocate(size);
            this.startIndex = 0;
            spNext(null);
            spPrev(prev);
            spIndex(index);
            sequence = null;
        }

        final boolean isPooled()
        {
            return sequence != null;
        }

        private static long calcSequenceOffset(long index)
        {
            return ARRAY_BASE + (index << ELEMENT_SHIFT);
        }

        final void soSequence(int index, long e)
        {
            UNSAFE.putOrderedLong(sequence, calcSequenceOffset(index + startIndex), e);
        }

        final long lvSequence(int index)
        {
            return UNSAFE.getLongVolatile(sequence, calcSequenceOffset(index + startIndex));
        }

        final AtomicChunk<E> lvNext()
        {
            return next;
        }

        final AtomicChunk<E> lpPrev()
        {
            return (AtomicChunk<E>) UNSAFE.getObject(this, PREV_OFFSET);
        }

        final long lvIndex()
        {
            return index;
        }

        final void soIndex(long index)
        {
            UNSAFE.putOrderedLong(this, INDEX_OFFSET, index);
        }

        final void spIndex(long index)
        {
            UNSAFE.putLong(this, INDEX_OFFSET, index);
        }

        final void soNext(AtomicChunk<E> value)
        {
            UNSAFE.putOrderedObject(this, NEXT_OFFSET, value);
        }

        final void spNext(AtomicChunk<E> value)
        {
            UNSAFE.putObject(this, NEXT_OFFSET, value);
        }

        final void spPrev(AtomicChunk<E> value)
        {
            UNSAFE.putObject(this, PREV_OFFSET, value);
        }

        final void soElement(int index, E e)
        {
            UnsafeRefArrayAccess.soElement(buffer, UnsafeRefArrayAccess.calcElementOffset(index + startIndex), e);
        }

        final void spElement(int index, E e)
        {
            UnsafeRefArrayAccess.spElement(buffer, UnsafeRefArrayAccess.calcElementOffset(index + startIndex), e);
        }

        final E lvElement(int index)
        {
            return UnsafeRefArrayAccess.lvElement(buffer, UnsafeRefArrayAccess.calcElementOffset(index + startIndex));
        }

    }
}

// $gen:ordered-fields
abstract class MpmcProgressiveChunkedQueueProducerFields<E> extends MpmcProgressiveChunkedQueuePad1<E>
{
    private final static long P_INDEX_OFFSET =
        fieldOffset(MpmcProgressiveChunkedQueueProducerFields.class, "producerIndex");
    private volatile long producerIndex;

    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    final long getAndIncrementProducerIndex()
    {
        return UNSAFE.getAndAddLong(this, P_INDEX_OFFSET, 1);
    }

    final long getAndAddProducerIndex(long delta)
    {
        return UNSAFE.getAndAddLong(this, P_INDEX_OFFSET, delta);
    }
}

abstract class MpmcProgressiveChunkedQueuePad2<E> extends MpmcProgressiveChunkedQueueProducerFields<E>
{
    long p01, p02, p03, p04, p05, p06, p07, p08;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpmcProgressiveChunkedQueueProducerBuffer<E> extends MpmcProgressiveChunkedQueuePad2<E>
{
    private static final long P_BUFFER_OFFSET =
        fieldOffset(MpmcProgressiveChunkedQueueProducerBuffer.class, "producerBuffer");
    private static final long P_CHUNK_INDEX_OFFSET =
        fieldOffset(MpmcProgressiveChunkedQueueProducerBuffer.class, "producerChunkIndex");

    private volatile AtomicChunk<E> producerBuffer;
    private volatile long producerChunkIndex;


    final long lvProducerChunkIndex()
    {
        return producerChunkIndex;
    }

    final boolean casProducerChunkIndex(long expected, long value)
    {
        return UNSAFE.compareAndSwapLong(this, P_CHUNK_INDEX_OFFSET, expected, value);
    }

    final void soProducerChunkIndex(long value)
    {
        UNSAFE.putOrderedLong(this, P_CHUNK_INDEX_OFFSET, value);
    }

    final AtomicChunk<E> lvProducerBuffer()
    {
        return this.producerBuffer;
    }

    final void soProducerBuffer(AtomicChunk<E> buffer)
    {
        UNSAFE.putOrderedObject(this, P_BUFFER_OFFSET, buffer);
    }
}

abstract class MpmcProgressiveChunkedQueuePad3<E> extends MpmcProgressiveChunkedQueueProducerBuffer<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpmcProgressiveChunkedQueueConsumerFields<E> extends MpmcProgressiveChunkedQueuePad3<E>
{
    private final static long C_INDEX_OFFSET =
        fieldOffset(MpmcProgressiveChunkedQueueConsumerFields.class, "consumerIndex");
    private final static long C_BUFFER_OFFSET =
        fieldOffset(MpmcProgressiveChunkedQueueConsumerFields.class, "consumerBuffer");

    private volatile long consumerIndex;
    private volatile AtomicChunk<E> consumerBuffer;

    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    final boolean casConsumerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
    }

    final AtomicChunk<E> lvConsumerBuffer()
    {
        return this.consumerBuffer;
    }

    final void soConsumerBuffer(AtomicChunk<E> newValue)
    {
        UNSAFE.putOrderedObject(this, C_BUFFER_OFFSET, newValue);
    }

}

abstract class MpmcProgressiveChunkedQueuePad5<E> extends MpmcProgressiveChunkedQueueConsumerFields<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

/**
 * An MPMC array queue which starts at <i>initialCapacity</i> and grows unbounded in linked chunks.<br>
 * Differently from {@link MpmcArrayQueue} it is designed to provide a better scaling when more
 * producers are concurrently offering.
 *
 * @param <E>
 * @author https://github.com/franz1981
 */
public class MpmcUnboundedXaddArrayQueue<E> extends MpmcProgressiveChunkedQueuePad5<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    private final int chunkMask;
    private final int chunkShift;
    private final SpscArrayQueue<AtomicChunk<E>> freeBuffer;

    public MpmcUnboundedXaddArrayQueue(int chunkSize, int maxPooledChunks)
    {
        chunkSize = Pow2.roundToPowerOfTwo(chunkSize);
        final int totalSize = chunkSize * (maxPooledChunks + 1);
        final E[] wholeBuffer = CircularArrayOffsetCalculator.allocate(totalSize);
        final long[] wholeSequence = new long[totalSize];
        Arrays.fill(wholeSequence, AtomicChunk.NIL_CHUNK_INDEX);
        final AtomicChunk<E> first = new AtomicChunk(0, null, wholeBuffer, wholeSequence, 0);
        soProducerBuffer(first);
        soProducerChunkIndex(0);
        soConsumerBuffer(first);
        chunkMask = chunkSize - 1;
        chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        freeBuffer = new SpscArrayQueue<AtomicChunk<E>>(maxPooledChunks + 1);
        for (int i = 0; i < maxPooledChunks; i++)
        {
            final int startIndex = chunkSize * (i + 1);
            freeBuffer.offer(new AtomicChunk(
                AtomicChunk.NIL_CHUNK_INDEX,
                null,
                wholeBuffer,
                wholeSequence,
                startIndex));
        }
    }

    public MpmcUnboundedXaddArrayQueue(int chunkSize)
    {
        this(chunkSize, 1);
    }

    private AtomicChunk<E> producerBufferOf(AtomicChunk<E> producerBuffer, long expectedChunkIndex)
    {
        long jumpBackward;
        while (true)
        {
            if (producerBuffer == null)
            {
                producerBuffer = lvProducerBuffer();
            }
            final long producerChunkIndex = producerBuffer.lvIndex();
            if (producerChunkIndex == AtomicChunk.NIL_CHUNK_INDEX)
            {
                //force an attempt to fetch it another time
                producerBuffer = null;
                continue;
            }
            jumpBackward = producerChunkIndex - expectedChunkIndex;
            if (jumpBackward >= 0)
            {
                break;
            }
            //try validate against the last producer chunk index
            if (lvProducerChunkIndex() == producerChunkIndex)
            {
                producerBuffer = appendNextChunk(producerBuffer, producerChunkIndex, chunkMask + 1);
            }
            else
            {
                producerBuffer = null;
            }
        }
        for (long i = 0; i < jumpBackward; i++)
        {
            //prev cannot be null, because is being released by index
            producerBuffer = producerBuffer.lpPrev();
            assert producerBuffer != null;
        }
        assert producerBuffer.lvIndex() == expectedChunkIndex;
        return producerBuffer;
    }

    private AtomicChunk<E> appendNextChunk(AtomicChunk<E> producerBuffer, long chunkIndex, int chunkSize)
    {
        assert chunkIndex != AtomicChunk.NIL_CHUNK_INDEX;
        final long nextChunkIndex = chunkIndex + 1;
        //prevent other concurrent attempts on appendNextChunk
        if (!casProducerChunkIndex(chunkIndex, nextChunkIndex))
        {
            return null;
        }
        AtomicChunk<E> newChunk = freeBuffer.poll();
        if (newChunk != null)
        {
            assert newChunk.lvIndex() == AtomicChunk.NIL_CHUNK_INDEX;
            //prevent other concurrent attempts on appendNextChunk
            soProducerBuffer(newChunk);
            newChunk.spPrev(producerBuffer);
            //index set is releasing prev, allowing other pending offers to continue
            newChunk.soIndex(nextChunkIndex);
        }
        else
        {
            newChunk = new AtomicChunk<E>(nextChunkIndex, producerBuffer, chunkSize);
            soProducerBuffer(newChunk);
        }
        //link the next chunk only when finished
        producerBuffer.soNext(newChunk);
        return newChunk;
    }

    @Override
    public long currentProducerIndex()
    {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex()
    {
        return lvConsumerIndex();
    }

    @Override
    public boolean offer(E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        final long producerSeq = getAndIncrementProducerIndex();
        final int pOffset = (int) (producerSeq & chunkMask);
        long chunkIndex = producerSeq >> chunkShift;
        AtomicChunk<E> producerBuffer = lvProducerBuffer();
        if (producerBuffer.lvIndex() != chunkIndex)
        {
            producerBuffer = producerBufferOf(producerBuffer, chunkIndex);
            if (producerBuffer.isPooled())
            {
                chunkIndex = producerBuffer.lvIndex();
            }
        }
        final boolean isPooled = producerBuffer.isPooled();
        if (isPooled)
        {
            //wait any previous consumer to finish its job
            while (producerBuffer.lvElement(pOffset) != null)
            {

            }
        }
        producerBuffer.soElement(pOffset, e);
        if (isPooled)
        {
            producerBuffer.soSequence(pOffset, chunkIndex);
        }
        return true;
    }

    private static <E> E spinForElement(AtomicChunk<E> chunk, int offset)
    {
        E e;
        while ((e = chunk.lvElement(offset)) == null)
        {

        }
        return e;
    }

    private void rotateConsumerBuffer(AtomicChunk<E> consumerBuffer, AtomicChunk<E> next)
    {
        next.spPrev(null);
        //save from nepotism
        consumerBuffer.spNext(null);
        //prevent other consumers to use it
        consumerBuffer.soIndex(AtomicChunk.NIL_CHUNK_INDEX);
        if (consumerBuffer.isPooled())
        {
            final boolean pooled = freeBuffer.offer(consumerBuffer);
            assert pooled;
        }
        //expose next to the other consumers
        soConsumerBuffer(next);
    }

    @Override
    public E poll()
    {
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        long consumerIndex;
        AtomicChunk<E> consumerBuffer;
        int consumerOffset;
        final int chunkSize = chunkMask + 1;
        boolean firstElementOfNewChunk;
        E e = null;
        AtomicChunk<E> next = null;
        long chunkIndex;
        do
        {
            consumerIndex = this.lvConsumerIndex();
            consumerBuffer = this.lvConsumerBuffer();
            consumerOffset = (int) (consumerIndex & chunkMask);
            chunkIndex = consumerIndex >> chunkShift;
            firstElementOfNewChunk = consumerOffset == 0 && consumerIndex >= chunkSize;
            if (firstElementOfNewChunk)
            {
                next = consumerBuffer.lvNext();
                final long expectedChunkIndex = chunkIndex - 1;
                if (expectedChunkIndex != consumerBuffer.lvIndex())
                {
                    //another consumer has already rotated the consumer buffer or is yet to rotating it
                    continue;
                }
                if (next == null)
                {
                    if (lvProducerIndex() == consumerIndex)
                    {
                        return null;
                    }
                    //if another consumer rotate consumerBuffer, its chunkIndex will change
                    while ((next = consumerBuffer.lvNext()) == null)
                    {
                        if (expectedChunkIndex != consumerBuffer.lvIndex())
                        {
                            //another consumer has already rotated the consumer buffer
                            break;
                        }
                    }
                    if (next == null)
                    {
                        continue;
                    }
                }
            }
            else
            {
                if (consumerBuffer.isPooled())
                {
                    if (consumerBuffer.lvSequence(consumerOffset) != chunkIndex)
                    {
                        if (lvProducerIndex() == consumerIndex)
                        {
                            return null;
                        }
                        continue;
                    }
                    e = consumerBuffer.lvElement(consumerOffset);
                    if (e == null)
                    {
                        //another consumer has already consumed the element
                        continue;
                    }
                }
                else
                {
                    e = consumerBuffer.lvElement(consumerOffset);
                    if (chunkIndex != consumerBuffer.lvIndex())
                    {
                        //another consumer has already rotated the consumer buffer or is yet to rotating it
                        continue;
                    }
                    if (e == null)
                    {
                        if (lvProducerIndex() == consumerIndex)
                        {
                            return null;
                        }
                        //if the buffer is not empty, another consumer could have already
                        //stolen it, incrementing consumerIndex too: better to check if it has happened
                        continue;
                    }
                }
            }
            if (casConsumerIndex(consumerIndex, consumerIndex + 1))
            {
                break;
            }
        }
        while (true);
        //if we are the firstElementOfNewChunk we need to rotate the consumer buffer
        if (firstElementOfNewChunk)
        {
            //we can freely spin awaiting producer, because we are the only one in charge to
            //rotate the consumer buffer and using next
            e = spinForElement(next, consumerOffset);
            final boolean pooled = next.isPooled();
            if (pooled)
            {
                while (next.lvSequence(consumerOffset) != chunkIndex)
                {

                }
            }
            next.soElement(consumerOffset, null);
            rotateConsumerBuffer(consumerBuffer, next);
        }
        else
        {
            assert !consumerBuffer.isPooled() ||
                (consumerBuffer.isPooled() && consumerBuffer.lvSequence(consumerOffset) == chunkIndex);
            consumerBuffer.soElement(consumerOffset, null);
        }
        return e;
    }

    @Override
    public E peek()
    {
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        long consumerIndex;
        AtomicChunk<E> consumerBuffer;
        int consumerOffset;
        boolean firstElementOfNewChunk;
        E e;
        AtomicChunk<E> next;
        do
        {
            consumerIndex = this.lvConsumerIndex();
            consumerBuffer = this.lvConsumerBuffer();
            final long chunkIndex = consumerIndex >> chunkShift;
            consumerOffset = (int) (consumerIndex & chunkMask);
            final int chunkSize = chunkMask + 1;
            firstElementOfNewChunk = consumerOffset == 0 && consumerIndex >= chunkSize;
            if (firstElementOfNewChunk)
            {
                final long expectedChunkIndex = chunkIndex - 1;
                next = consumerBuffer.lvNext();
                if (expectedChunkIndex != consumerBuffer.lvIndex())
                {
                    //another consumer has already rotated the consumer buffer or is yet to rotating it
                    continue;
                }
                if (next == null)
                {
                    if (lvProducerIndex() == consumerIndex)
                    {
                        return null;
                    }
                    //if another consumer rotate consumerBuffer, its chunkIndex will change
                    while ((next = consumerBuffer.lvNext()) == null)
                    {
                        if (expectedChunkIndex != consumerBuffer.lvIndex())
                        {
                            //another consumer has already rotated the consumer buffer
                            break;
                        }
                    }
                    if (next == null)
                    {
                        continue;
                    }
                }
                consumerBuffer = next;
            }
            e = consumerBuffer.lvElement(consumerOffset);
            if (e != null)
            {
                //validate the element read
                if (chunkIndex != consumerBuffer.lvIndex())
                {
                    //another consumer has already rotated the consumer buffer or is yet to rotating it
                    continue;
                }
                return e;
            }
            else
            {
                if (lvProducerIndex() == consumerIndex)
                {
                    return null;
                }
                //if the q is not empty, another consumer could have already
                //stolen it, incrementing consumerIndex too: better to check if it has happened
                continue;
            }
        }
        while (true);
    }

    @Override
    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size()
    {
        return IndexedQueueSizeUtil.size(this);
    }

    @Override
    public int capacity()
    {
        return MessagePassingQueue.UNBOUNDED_CAPACITY;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        return offer(e);
    }

    @Override
    public E relaxedPoll()
    {
        return poll();
    }

    @Override
    public E relaxedPeek()
    {
        return peek();
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return drain(c, chunkMask + 1);
    }

    @Override
    public int fill(Supplier<E> s)
    {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        final int capacity = chunkMask + 1;
        final int offerBatch = Math.min(PortableJvmInfo.RECOMENDED_OFFER_BATCH, capacity);
        do
        {
            final int filled = fill(s, offerBatch);
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
    public int drain(Consumer<E> c, int limit)
    {
        for (int i = 0; i < limit; i++)
        {
            final E e = relaxedPoll();
            if (e == null)
            {
                return i;
            }
            c.accept(e);
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit)
    {
        final int chunkShift = this.chunkShift;
        final int chunkMask = this.chunkMask;
        long producerSeq = getAndAddProducerIndex(limit);
        AtomicChunk<E> producerBuffer = null;
        for (int i = 0; i < limit; i++)
        {
            final int pOffset = (int) (producerSeq & chunkMask);
            long chunkIndex = producerSeq >> chunkShift;
            if (producerBuffer == null || producerBuffer.lvIndex() != chunkIndex)
            {
                producerBuffer = producerBufferOf(producerBuffer, chunkIndex);
                if (producerBuffer.isPooled())
                {
                    chunkIndex = producerBuffer.lvIndex();
                }
            }
            if (producerBuffer.isPooled())
            {
                while (producerBuffer.lvElement(pOffset) != null)
                {

                }
            }
            producerBuffer.soElement(pOffset, s.get());
            if (producerBuffer.isPooled())
            {
                producerBuffer.soSequence(pOffset, chunkIndex);
            }
            producerSeq++;
        }
        return limit;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, wait, exit);
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        while (exit.keepRunning())
        {
            fill(s, PortableJvmInfo.RECOMENDED_OFFER_BATCH);
        }
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

}
