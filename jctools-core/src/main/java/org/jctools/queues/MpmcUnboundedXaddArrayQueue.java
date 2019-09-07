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
import static org.jctools.util.UnsafeRefArrayAccess.lpElement;


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

        AtomicChunk(long index, AtomicChunk<E> prev, int size, boolean pooled)
        {
            buffer = CircularArrayOffsetCalculator.allocate(size);
            spNext(null);
            spPrev(prev);
            spIndex(index);
            if (pooled)
            {
                sequence = new long[size];
                Arrays.fill(sequence, AtomicChunk.NIL_CHUNK_INDEX);
            }
            else
            {
                sequence = null;
            }
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
            UNSAFE.putOrderedLong(sequence, calcSequenceOffset(index), e);
        }

        final long lvSequence(int index)
        {
            return UNSAFE.getLongVolatile(sequence, calcSequenceOffset(index));
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
        final AtomicChunk<E> first = new AtomicChunk(0, null, chunkSize, true);
        soProducerBuffer(first);
        soProducerChunkIndex(0);
        soConsumerBuffer(first);
        chunkMask = chunkSize - 1;
        chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        freeBuffer = new SpscArrayQueue<AtomicChunk<E>>(maxPooledChunks + 1);
        for (int i = 0; i < maxPooledChunks; i++)
        {
            freeBuffer.offer(new AtomicChunk(AtomicChunk.NIL_CHUNK_INDEX, null, chunkSize, true));
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
            newChunk = new AtomicChunk<E>(nextChunkIndex, producerBuffer, chunkSize, false);
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

    private E rotateConsumerBuffer(AtomicChunk<E> consumerBuffer, AtomicChunk<E> next, int consumerOffset, long expectedChunkIndex)
    {
        while (next == null)
        {
            next = consumerBuffer.lvNext();
        }
        //prevent other consumers to use it, but need to await next != null
        //or the producer won't be able to append next on a NIL_CHUNK_INDEX!
        consumerBuffer.soIndex(AtomicChunk.NIL_CHUNK_INDEX);
        //we can freely spin awaiting producer, because we are the only one in charge to
        //rotate the consumer buffer and use next
        final E e = spinForElement(next, consumerOffset);
        final boolean pooled = next.isPooled();
        if (pooled)
        {
            while (next.lvSequence(consumerOffset) != expectedChunkIndex)
            {

            }
        }
        next.soElement(consumerOffset, null);
        next.spPrev(null);
        //save from nepotism
        consumerBuffer.spNext(null);
        if (consumerBuffer.isPooled())
        {
            final boolean offered = freeBuffer.offer(consumerBuffer);
            assert offered;
        }
        //expose next to the other consumers
        soConsumerBuffer(next);
        return e;
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
        long pIndex = -1; // start with bogus value, hope we don't need it
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
                //we don't care about < or >, because if:
                //- consumerBuffer::index < expectedChunkIndex: another consumer has rotated consumerBuffer,
                //  but not reused (yet, if possible)
                //- consumerBuffer::index > expectedChunkIndex: another consumer has rotated consumerBuffer,
                // that has been pooled and reused again
                //In both cases we have a stale view of the world with a not reliable next value.
                if (expectedChunkIndex != consumerBuffer.lvIndex())
                {
                    continue;
                }
                if (next == null)
                {
                    if (consumerIndex >= pIndex && // test against cached pIndex
                        consumerIndex == (pIndex = lvProducerIndex()))
                    { // update pIndex if we must
                        // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                        return null;
                    }
                    //not empty: can attempt the cas
                }
            }
            else
            {
                final boolean pooled = consumerBuffer.isPooled();
                e = pooled ? null : consumerBuffer.lvElement(consumerOffset);
                final long index = pooled ? consumerBuffer.lvSequence(consumerOffset) : consumerBuffer.lvIndex();
                if (index != chunkIndex)
                {
                    if (index < chunkIndex)
                    {
                        // if pooled:
                        // a) consumerBuffer::index > chunkIndex: a chunk used in the past or at its first use
                        // b) consumerBuffer::index < chunkIndex: a rotation is in progress
                        // c) consumerBuffer::index == chunkIndex: rotation is happened, not yet an element in
                        // For a) there is no need to check q emptiness, but doing it isn't wrong,
                        // because the check will fail (ie q isn't empty re consumerIndex):
                        // consumerBuffer::index > chunkIndex means that others have proceeded consuming new elements.
                        // For b) and c) the emptiness check is necessary, because if there are no other elements
                        // poll *must* return null.
                        //
                        // if !pooled:
                        // - the rotation isn't happened yet
                        if (consumerIndex >= pIndex && // test against cached pIndex
                            consumerIndex == (pIndex = lvProducerIndex()))
                        { // update pIndex if we must
                            // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                            return null;
                        }
                        continue;
                    }
                    else
                    {
                        //Stale view of the world: retry!
                        continue;
                    }
                }
                assert index == chunkIndex;
                if (!pooled && e == null)
                {
                    if (consumerIndex >= pIndex && // test against cached pIndex
                        consumerIndex == (pIndex = lvProducerIndex()))
                    { // update pIndex if we must
                        // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                        return null;
                    }
                    //we are awaiting the producer here
                    continue;
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
            e = rotateConsumerBuffer(consumerBuffer, next, consumerOffset, chunkIndex);
        }
        else
        {
            if (consumerBuffer.isPooled()) {
                e = consumerBuffer.lvElement(consumerOffset);
                assert e != null;
            }
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
        final int chunkSize = chunkMask + 1;
        long consumerIndex;
        E e;
        do
        {
            e = null;
            consumerIndex = this.lvConsumerIndex();
            AtomicChunk<E> consumerBuffer = this.lvConsumerBuffer();
            final int consumerOffset = (int) (consumerIndex & chunkMask);
            final long chunkIndex = consumerIndex >> chunkShift;
            final boolean firstElementOfNewChunk = consumerOffset == 0 && consumerIndex >= chunkSize;
            if (firstElementOfNewChunk)
            {
                AtomicChunk<E> next = consumerBuffer.lvNext();
                final long expectedChunkIndex = chunkIndex - 1;
                if (expectedChunkIndex != consumerBuffer.lvIndex())
                {
                    continue;
                }
                if (next == null)
                {
                    continue;
                }
                consumerBuffer = next;
            }
            if (consumerBuffer.isPooled())
            {
                if (consumerBuffer.lvSequence(consumerOffset) != chunkIndex)
                {
                    continue;
                }
            }
            e = consumerBuffer.lvElement(consumerOffset);
            if (consumerBuffer.lvIndex() != chunkIndex)
            {
                e = null;
                continue;
            }
        }
        while (e == null && consumerIndex != lvProducerIndex());
        return e;
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
