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
import org.jctools.util.UnsafeRefArrayAccess;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;


abstract class MpscProgressiveChunkedQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    static final class AtomicChunk<E>
    {
        final static int NIL_CHUNK_INDEX = -1;
        private static final long PREV_OFFSET = fieldOffset(AtomicChunk.class, "prev");
        private static final long NEXT_OFFSET = fieldOffset(AtomicChunk.class, "next");
        private static final long INDEX_OFFSET = fieldOffset(AtomicChunk.class, "index");
        private volatile AtomicChunk<E> prev;
        private volatile long index;
        private volatile AtomicChunk<E> next;
        private final E[] buffer;
        private final boolean pooled;

        AtomicChunk(long index, AtomicChunk<E> prev, int size, boolean pooled)
        {
            buffer = CircularArrayOffsetCalculator.allocate(size);
            spNext(null);
            spPrev(prev);
            spIndex(index);
            this.pooled = pooled;
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
            UnsafeRefArrayAccess.soElement(buffer, UnsafeRefArrayAccess.calcElementOffset(index), e);
        }

        final void spElement(int index, E e)
        {
            UnsafeRefArrayAccess.spElement(buffer, UnsafeRefArrayAccess.calcElementOffset(index), e);
        }

        final E lvElement(int index)
        {
            return UnsafeRefArrayAccess.lvElement(buffer, UnsafeRefArrayAccess.calcElementOffset(index));
        }

        public boolean isPooled()
        {
            return pooled;
        }
    }
}

// $gen:ordered-fields
abstract class MpscProgressiveChunkedQueueProducerFields<E> extends MpscProgressiveChunkedQueuePad1<E>
{
    private final static long P_INDEX_OFFSET =
        fieldOffset(MpscProgressiveChunkedQueueProducerFields.class, "producerIndex");
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

abstract class MpscProgressiveChunkedQueuePad2<E> extends MpscProgressiveChunkedQueueProducerFields<E>
{
    long p01, p02, p03, p04, p05, p06, p07, p08;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpscProgressiveChunkedQueueProducerBuffer<E> extends MpscProgressiveChunkedQueuePad2<E>
{
    private static final long P_BUFFER_OFFSET =
        fieldOffset(MpscProgressiveChunkedQueueProducerBuffer.class, "producerBuffer");
    private static final long P_CHUNK_INDEX_OFFSET =
        fieldOffset(MpscProgressiveChunkedQueueProducerBuffer.class, "producerChunkIndex");

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

abstract class MpscProgressiveChunkedQueuePad3<E> extends MpscProgressiveChunkedQueueProducerBuffer<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpscProgressiveChunkedQueueConsumerFields<E> extends MpscProgressiveChunkedQueuePad3<E>
{
    private final static long C_INDEX_OFFSET =
        fieldOffset(MpscProgressiveChunkedQueueConsumerFields.class, "consumerIndex");

    private volatile long consumerIndex;
    protected AtomicChunk<E> consumerBuffer;

    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    final long lpConsumerIndex()
    {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }

    final void soConsumerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }
}

abstract class MpscProgressiveChunkedQueuePad4<E> extends MpscProgressiveChunkedQueueConsumerFields<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

/**
 * An MPSC array queue which starts at <i>initialCapacity</i> and grows unbounded in linked chunks.<br>
 * Differently from {@link MpscUnboundedArrayQueue} it is designed to provide a better scaling when more
 * producers are concurrently offering.
 *
 * @param <E>
 * @author https://github.com/franz1981
 */
public class MpscUnboundedXaddArrayQueue<E> extends MpscProgressiveChunkedQueuePad4<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    private static final long ROTATION = -2;
    private final int chunkMask;
    private final int chunkShift;
    private final SpscArrayQueue<AtomicChunk<E>> freeBuffer;

    public MpscUnboundedXaddArrayQueue(int chunkSize, int maxPooledChunks)
    {
        chunkSize = Pow2.roundToPowerOfTwo(chunkSize);
        final AtomicChunk<E> first = new AtomicChunk(0, null, chunkSize, true);
        soProducerBuffer(first);
        soProducerChunkIndex(0);
        consumerBuffer = first;
        chunkMask = chunkSize - 1;
        chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        freeBuffer = new SpscArrayQueue<AtomicChunk<E>>(maxPooledChunks + 1);
        for (int i = 0; i < maxPooledChunks; i++)
        {
            freeBuffer.offer(new AtomicChunk(AtomicChunk.NIL_CHUNK_INDEX, null, chunkSize, true));
        }
    }

    public MpscUnboundedXaddArrayQueue(int chunkSize)
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
                producerBuffer = appendNextChunks(producerBuffer, producerChunkIndex, chunkMask + 1, -jumpBackward);
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

    private AtomicChunk<E> appendNextChunks(AtomicChunk<E> producerBuffer, long chunkIndex, int chunkSize, long chunks)
    {
        assert chunkIndex != AtomicChunk.NIL_CHUNK_INDEX;
        //prevent other concurrent attempts on appendNextChunk
        if (!casProducerChunkIndex(chunkIndex, ROTATION))
        {
            return null;
        }
        AtomicChunk<E> newChunk = null;
        for (long i = 1; i <= chunks; i++)
        {
            final long nextChunkIndex = chunkIndex + i;
            newChunk = freeBuffer.poll();
            if (newChunk != null)
            {
                //single-writer: producerBuffer::index == nextChunkIndex is protecting it
                assert newChunk.lvIndex() == AtomicChunk.NIL_CHUNK_INDEX;
                newChunk.spPrev(producerBuffer);
                //index set is releasing prev, allowing other pending offers to continue
                newChunk.soIndex(nextChunkIndex);
            }
            else
            {
                newChunk = new AtomicChunk<E>(nextChunkIndex, producerBuffer, chunkSize, false);
            }
            soProducerBuffer(newChunk);
            //link the next chunk only when finished
            producerBuffer.soNext(newChunk);
            producerBuffer = newChunk;
        }
        soProducerChunkIndex(chunkIndex + chunks);
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
        final long chunkIndex = producerSeq >> chunkShift;
        AtomicChunk<E> producerBuffer = lvProducerBuffer();
        if (producerBuffer.lvIndex() != chunkIndex)
        {
            producerBuffer = producerBufferOf(producerBuffer, chunkIndex);
        }
        producerBuffer.soElement(pOffset, e);
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

    private AtomicChunk<E> spinForNextIfNotEmpty(AtomicChunk<E> consumerBuffer, long consumerIndex)
    {
        AtomicChunk<E> next = consumerBuffer.lvNext();
        if (next == null)
        {
            if (lvProducerIndex() == consumerIndex)
            {
                return null;
            }
            while ((next = consumerBuffer.lvNext()) == null)
            {

            }
        }
        return next;
    }


    private AtomicChunk<E> pollNextBuffer(AtomicChunk<E> consumerBuffer, long consumerIndex)
    {
        final AtomicChunk<E> next = spinForNextIfNotEmpty(consumerBuffer, consumerIndex);
        if (next == null)
        {
            return null;
        }
        //save from nepotism
        consumerBuffer.spNext(null);
        //change the chunkIndex to a non valid value
        //to stop offering threads to use this buffer
        consumerBuffer.soIndex(AtomicChunk.NIL_CHUNK_INDEX);
        if (consumerBuffer.isPooled())
        {
            final boolean pooled = freeBuffer.offer(consumerBuffer);
            assert pooled;
        }
        next.spPrev(null);
        return next;
    }

    @Override
    public E poll()
    {
        final int chunkMask = this.chunkMask;
        final long consumerIndex = this.lpConsumerIndex();
        AtomicChunk<E> consumerBuffer = this.consumerBuffer;
        final int consumerOffset = (int) (consumerIndex & chunkMask);
        final int chunkSize = chunkMask + 1;
        final boolean firstElementOfNewChunk = consumerOffset == 0 && consumerIndex >= chunkSize;
        if (firstElementOfNewChunk)
        {
            consumerBuffer = pollNextBuffer(consumerBuffer, consumerIndex);
            if (consumerBuffer == null)
            {
                return null;
            }
            this.consumerBuffer = consumerBuffer;
        }
        else
        {
            final E e = consumerBuffer.lvElement(consumerOffset);
            if (e != null)
            {
                consumerBuffer.spElement(consumerOffset, null);
                soConsumerIndex(consumerIndex + 1);
                return e;
            }
            if (lvProducerIndex() == consumerIndex)
            {
                return null;
            }
        }
        final E e = spinForElement(consumerBuffer, consumerOffset);
        consumerBuffer.spElement(consumerOffset, null);
        soConsumerIndex(consumerIndex + 1);
        return e;
    }

    @Override
    public E peek()
    {
        final int chunkMask = this.chunkMask;
        final long consumerIndex = this.lpConsumerIndex();
        AtomicChunk<E> consumerBuffer = this.consumerBuffer;
        final int consumerOffset = (int) (consumerIndex & chunkMask);
        final int chunkSize = chunkMask + 1;
        final boolean firstElementOfNewChunk = consumerOffset == 0 && consumerIndex >= chunkSize;
        if (firstElementOfNewChunk)
        {
            final AtomicChunk<E> next = spinForNextIfNotEmpty(consumerBuffer, consumerIndex);
            if (next == null)
            {
                return null;
            }
            consumerBuffer = next;
        }
        else
        {
            final E e = consumerBuffer.lvElement(consumerOffset);
            if (e != null)
            {
                return e;
            }
            if (lvProducerIndex() == consumerIndex)
            {
                return null;
            }
        }
        return spinForElement(consumerBuffer, consumerOffset);
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

    private AtomicChunk<E> relaxedPollNextBuffer(AtomicChunk<E> consumerBuffer)
    {
        final AtomicChunk<E> next = consumerBuffer.lvNext();
        if (next == null)
        {
            return null;
        }
        //save from nepotism
        consumerBuffer.spNext(null);
        //change the chunkIndex to a non valid value
        //to stop offering threads to use this buffer
        consumerBuffer.soIndex(AtomicChunk.NIL_CHUNK_INDEX);
        if (consumerBuffer.isPooled())
        {
            final boolean pooled = freeBuffer.offer(consumerBuffer);
            assert pooled;
        }
        next.spPrev(null);
        return next;
    }

    @Override
    public E relaxedPoll()
    {
        final int chunkMask = this.chunkMask;
        final long consumerIndex = this.lpConsumerIndex();
        AtomicChunk<E> consumerBuffer = this.consumerBuffer;
        final int consumerOffset = (int) (consumerIndex & chunkMask);
        final int chunkSize = chunkMask + 1;
        final boolean firstElementOfNewChunk = consumerOffset == 0 && consumerIndex >= chunkSize;
        E e;
        if (firstElementOfNewChunk)
        {
            consumerBuffer = relaxedPollNextBuffer(consumerBuffer);
            if (consumerBuffer == null)
            {
                return null;
            }
            this.consumerBuffer = consumerBuffer;
            //the element on consumerIndex can't be null from now on
            e = spinForElement(consumerBuffer, 0);
        }
        else
        {
            e = consumerBuffer.lvElement(consumerOffset);
            if (e == null)
            {
                return null;
            }
        }
        consumerBuffer.spElement(consumerOffset, null);
        soConsumerIndex(consumerIndex + 1);
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        final int chunkMask = this.chunkMask;
        final long consumerIndex = this.lpConsumerIndex();
        AtomicChunk<E> consumerBuffer = this.consumerBuffer;
        final int consumerOffset = (int) (consumerIndex & chunkMask);
        final int chunkSize = chunkMask + 1;
        final boolean firstElementOfNewChunk = consumerOffset == 0 && consumerIndex >= chunkSize;
        if (firstElementOfNewChunk)
        {
            final AtomicChunk<E> next = consumerBuffer.lvNext();
            if (next == null)
            {
                return null;
            }
            consumerBuffer = next;
            //the element on consumerIndex can't be null from now on
        }
        final E e = consumerBuffer.lvElement(consumerOffset);
        if (e != null)
        {
            return e;
        }
        assert !firstElementOfNewChunk;
        return null;
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
        final int chunkMask = this.chunkMask;
        final int chunkSize = chunkMask + 1;
        long consumerIndex = this.lpConsumerIndex();
        AtomicChunk<E> consumerBuffer = this.consumerBuffer;

        for (int i = 0; i < limit; i++)
        {
            final int consumerOffset = (int) (consumerIndex & chunkMask);
            final boolean firstElementOfNewChunk = consumerOffset == 0 && consumerIndex >= chunkSize;
            E e;
            if (firstElementOfNewChunk)
            {
                consumerBuffer = relaxedPollNextBuffer(consumerBuffer);
                if (consumerBuffer == null)
                {
                    return i;
                }
                this.consumerBuffer = consumerBuffer;
                e = spinForElement(consumerBuffer, 0);
            }
            else
            {
                e = consumerBuffer.lvElement(consumerOffset);
                if (e == null)
                {
                    return i;
                }
            }
            consumerBuffer.spElement(consumerOffset, null);
            final long nextConsumerIndex = consumerIndex + 1;
            soConsumerIndex(nextConsumerIndex);
            c.accept(e);
            consumerIndex = nextConsumerIndex;
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
            final long chunkIndex = producerSeq >> chunkShift;
            if (producerBuffer == null || producerBuffer.lvIndex() != chunkIndex)
            {
                producerBuffer = producerBufferOf(producerBuffer, chunkIndex);
            }
            producerBuffer.soElement(pOffset, s.get());
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
