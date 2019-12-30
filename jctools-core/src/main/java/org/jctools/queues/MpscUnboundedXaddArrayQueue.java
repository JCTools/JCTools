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

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;


abstract class MpscUnboundedXaddArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

// $gen:ordered-fields
abstract class MpscUnboundedXaddArrayQueueProducerFields<E> extends MpscUnboundedXaddArrayQueuePad1<E>
{
    private final static long P_INDEX_OFFSET =
        fieldOffset(MpscUnboundedXaddArrayQueueProducerFields.class, "producerIndex");
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

abstract class MpscUnboundedXaddArrayQueuePad2<E> extends MpscUnboundedXaddArrayQueueProducerFields<E>
{
    long p01, p02, p03, p04, p05, p06, p07, p08;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpscUnboundedXaddArrayQueueProducerBuffer<E> extends MpscUnboundedXaddArrayQueuePad2<E>
{
    private static final long P_CHUNK_OFFSET =
        fieldOffset(MpscUnboundedXaddArrayQueueProducerBuffer.class, "producerChunk");
    private static final long P_CHUNK_INDEX_OFFSET =
        fieldOffset(MpscUnboundedXaddArrayQueueProducerBuffer.class, "producerChunkIndex");

    private volatile MpscUnboundedXaddChunk<E> producerChunk;
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

    final MpscUnboundedXaddChunk<E> lvProducerChunk()
    {
        return this.producerChunk;
    }

    final void soProducerChunk(MpscUnboundedXaddChunk<E> chunk)
    {
        UNSAFE.putOrderedObject(this, P_CHUNK_OFFSET, chunk);
    }
}

abstract class MpscUnboundedXaddArrayQueuePad3<E> extends MpscUnboundedXaddArrayQueueProducerBuffer<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpscUnboundedXaddArrayQueueConsumerFields<E> extends MpscUnboundedXaddArrayQueuePad3<E>
{
    private final static long C_INDEX_OFFSET =
        fieldOffset(MpscUnboundedXaddArrayQueueConsumerFields.class, "consumerIndex");

    private volatile long consumerIndex;
    protected MpscUnboundedXaddChunk<E> consumerChunk;

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

abstract class MpscUnboundedXaddArrayQueuePad4<E> extends MpscUnboundedXaddArrayQueueConsumerFields<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

/**
 * An MPSC array queue which starts at <i>initialCapacity</i> and grows unbounded in linked chunks.<br>
 * Differently from {@link MpscUnboundedArrayQueue} it is designed to provide a better scaling when more
 * producers are concurrently offering.
 *
 * @author https://github.com/franz1981
 */
public class MpscUnboundedXaddArrayQueue<E> extends MpscUnboundedXaddArrayQueuePad4<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    private static final long ROTATION = -2;
    private final int chunkMask;
    private final int chunkShift;
    private final SpscArrayQueue<MpscUnboundedXaddChunk<E>> freeChunksPool;

    public MpscUnboundedXaddArrayQueue(int chunkSize, int maxPooledChunks)
    {
        if (!UnsafeAccess.SUPPORTS_GET_AND_ADD_LONG)
        {
            throw new IllegalStateException("Unsafe::getAndAddLong support (JDK 8+) is required for this queue to work");
        }
        chunkSize = Pow2.roundToPowerOfTwo(chunkSize);
        final MpscUnboundedXaddChunk<E> first = new MpscUnboundedXaddChunk(0, null, chunkSize, true);
        soProducerChunk(first);
        soProducerChunkIndex(0);
        consumerChunk = first;
        chunkMask = chunkSize - 1;
        chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        freeChunksPool = new SpscArrayQueue<MpscUnboundedXaddChunk<E>>(maxPooledChunks + 1);
        for (int i = 0; i < maxPooledChunks; i++)
        {
            freeChunksPool.offer(
                new MpscUnboundedXaddChunk(MpscUnboundedXaddChunk.CHUNK_CONSUMED, null, chunkSize, true));
        }
    }

    public MpscUnboundedXaddArrayQueue(int chunkSize)
    {
        this(chunkSize, 1);
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

        final long pIndex = getAndIncrementProducerIndex();

        final int piChunkOffset = (int) (pIndex & chunkMask);
        final long piChunkIndex = pIndex >> chunkShift;

        MpscUnboundedXaddChunk<E> pChunk = lvProducerChunk();
        if (pChunk.lvIndex() != piChunkIndex)
        {
            // Other producers may have advanced the producer chunk as we claimed a slot in a prev chunk, or we may have
            // now stepped into a brand new chunk which needs appending.
            pChunk = producerChunkForIndex(pChunk, piChunkIndex);
        }
        pChunk.soElement(piChunkOffset, e);
        return true;
    }

    /**
     * We're here because pChunk.index doesn't match the expectedChunkIndex. To resolve we must now chase the linked
     * chunks to the appropriate chunk. More than one producer may end up racing to add or discover new chunks.
     *
     * @param initialChunk the starting point chunk, which does not match the required chunk index
     * @param requiredChunkIndex the chunk index we need
     * @return the chunk matching the required index
     */
    private MpscUnboundedXaddChunk<E> producerChunkForIndex(
        final MpscUnboundedXaddChunk<E> initialChunk,
        final long requiredChunkIndex)
    {
        MpscUnboundedXaddChunk<E> currentChunk = initialChunk;
        long jumpBackward;
        while (true)
        {
            if (currentChunk == null)
            {
                currentChunk = lvProducerChunk();
            }
            final long currentChunkIndex = currentChunk.lvIndex();
            // Consumer will set the chunk index to CHUNK_CONSUMED when it is consumed, we should only see this case
            // if the consumer has done so concurrent to our attempts to use it.
            if (currentChunkIndex == MpscUnboundedXaddChunk.CHUNK_CONSUMED)
            {
                // force an attempt to fetch it another time
                currentChunk = null;
                continue;
            }
            // if the required chunk index is less than the current chunk index then we need to walk the linked list of
            // chunks back to the required index
            jumpBackward = currentChunkIndex - requiredChunkIndex;
            if (jumpBackward >= 0)
            {
                break;
            }
            //try validate against the last producer chunk index
            if (lvProducerChunkIndex() == currentChunkIndex)
            {
                long requiredChunks = -jumpBackward;
                currentChunk = appendNextChunks(currentChunk, currentChunkIndex, requiredChunks);
            }
            else
            {
                currentChunk = null;
            }
        }
        for (long i = 0; i < jumpBackward; i++)
        {
            // prev cannot be null, because the consumer cannot null it without consuming the element for which we are
            // trying to get the chunk.
            currentChunk = currentChunk.lpPrev();
            assert currentChunk != null;
        }
        assert currentChunk.lvIndex() == requiredChunkIndex;
        return currentChunk;
    }

    private MpscUnboundedXaddChunk<E> appendNextChunks(
        MpscUnboundedXaddChunk<E> currentChunk,
        long currentChunkIndex,
        long chunksToAppend)
    {
        assert currentChunkIndex != MpscUnboundedXaddChunk.CHUNK_CONSUMED;
        // prevent other concurrent attempts on appendNextChunk
        if (!casProducerChunkIndex(currentChunkIndex, ROTATION))
        {
            return null;
        }
        /* LOCKED FOR APPEND */
        {
            long lvIndex;
            // it is valid for the currentChunk to be consumed while appending is in flight, but it's not valid for the
            // current chunk ordering to change otherwise.
            assert ((lvIndex = currentChunk.lvIndex()) == MpscUnboundedXaddChunk.CHUNK_CONSUMED ||
                currentChunkIndex == lvIndex);

            MpscUnboundedXaddChunk<E> newChunk = null;
            for (long i = 1; i <= chunksToAppend; i++)
            {
                newChunk = newChunk(currentChunk, currentChunkIndex + i);
                soProducerChunk(newChunk);
                //link the next chunk only when finished
                currentChunk.soNext(newChunk);
                currentChunk = newChunk;
            }

            // release appending
            soProducerChunkIndex(currentChunkIndex + chunksToAppend);
        }
        /* UNLOCKED FOR APPEND */

        return currentChunk;
    }

    private MpscUnboundedXaddChunk<E> newChunk(MpscUnboundedXaddChunk<E> prevChunk, long nextChunkIndex)
    {
        MpscUnboundedXaddChunk<E> newChunk;
        newChunk = freeChunksPool.poll();
        if (newChunk != null)
        {
            //single-writer: prevChunk::index == nextChunkIndex is protecting it
            assert newChunk.lvIndex() == MpscUnboundedXaddChunk.CHUNK_CONSUMED;
            newChunk.spPrev(prevChunk);
            //index set is releasing prev, allowing other pending offers to continue
            newChunk.soIndex(nextChunkIndex);
        }
        else
        {
            newChunk = new MpscUnboundedXaddChunk<E>(nextChunkIndex, prevChunk, chunkMask + 1, false);
        }
        return newChunk;
    }



    @Override
    public E poll()
    {
        final int chunkMask = this.chunkMask;
        final long cIndex = this.lpConsumerIndex();
        final int ciChunkOffset = (int) (cIndex & chunkMask);

        MpscUnboundedXaddChunk<E> cChunk = this.consumerChunk;
        // start of new chunk?
        if (ciChunkOffset == 0 && cIndex != 0)
        {
            // pollNextBuffer will verify emptiness check
            cChunk = pollNextBuffer(cChunk, cIndex);
            if (cChunk == null)
            {
                return null;
            }
        }

        E e = cChunk.lvElement(ciChunkOffset);
        if (e == null)
        {
            if (lvProducerIndex() == cIndex)
            {
                return null;
            }
            else
            {
                e = cChunk.spinForElement(ciChunkOffset, false);
            }
        }
        cChunk.soElement(ciChunkOffset, null);
        soConsumerIndex(cIndex + 1);
        return e;
    }

    @Override
    public E peek()
    {
        final int chunkMask = this.chunkMask;
        final long cIndex = this.lpConsumerIndex();
        final int ciChunkOffset = (int) (cIndex & chunkMask);

        MpscUnboundedXaddChunk<E> cChunk = this.consumerChunk;
        // start of new chunk?
        if (ciChunkOffset == 0 && cIndex != 0)
        {
            cChunk = spinForNextIfNotEmpty(cChunk, cIndex);
            if (cChunk == null)
            {
                return null;
            }
        }

        E e = cChunk.lvElement(ciChunkOffset);
        if (e == null)
        {
            if (lvProducerIndex() == cIndex)
            {
                return null;
            }
            else
            {
                e = cChunk.spinForElement(ciChunkOffset, false);
            }
        }
        return e;
    }

    private MpscUnboundedXaddChunk<E> pollNextBuffer(MpscUnboundedXaddChunk<E> cChunk, long cIndex)
    {
        final MpscUnboundedXaddChunk<E> next = spinForNextIfNotEmpty(cChunk, cIndex);

        if (next == null)
        {
            return null;
        }

        moveToNextChunk(cIndex, cChunk, next);
        return next;
    }

    private MpscUnboundedXaddChunk<E> spinForNextIfNotEmpty(MpscUnboundedXaddChunk<E> cChunk, long cIndex)
    {
        MpscUnboundedXaddChunk<E> next = cChunk.lvNext();
        if (next == null)
        {
            if (lvProducerIndex() == cIndex)
            {
                return null;
            }
            while ((next = cChunk.lvNext()) == null)
            {

            }
        }
        return next;
    }

    private void moveToNextChunk(long cIndex, MpscUnboundedXaddChunk<E> cChunk, MpscUnboundedXaddChunk<E> next)
    {
        // avoid GC nepotism
        cChunk.soNext(null);// change the chunkIndex to a non valid value to stop offering threads to use this chunk
        cChunk.soIndex(MpscUnboundedXaddChunk.CHUNK_CONSUMED);
        if (cChunk.isPooled())
        {
            final boolean pooled = freeChunksPool.offer(cChunk);
            assert pooled;
        }
        next.spPrev(null);
        assert next.lvIndex() == cIndex >> chunkShift;
        this.consumerChunk = next;
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
    public boolean isEmpty()
    {
        return IndexedQueueSizeUtil.isEmpty(this);
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
        final int chunkMask = this.chunkMask;
        final long cIndex = this.lpConsumerIndex();
        final int ciChunkOffset = (int) (cIndex & chunkMask);

        MpscUnboundedXaddChunk<E> cChunk = this.consumerChunk;
        E e;
        // start of new chunk?
        if (ciChunkOffset == 0 && cIndex != 0)
        {
            final MpscUnboundedXaddChunk<E> next = cChunk.lvNext();
            if (next == null)
            {
                return null;
            }
            e = next.lvElement(0);

            // if the next chunk doesn't have the first element set we give up
            if (e == null)
            {
                return null;
            }
            moveToNextChunk(cIndex, cChunk, next);

            cChunk = next;
        }
        else
        {
            e = cChunk.lvElement(ciChunkOffset);
            if (e == null)
            {
                return null;
            }
        }

        cChunk.soElement(ciChunkOffset, null);
        soConsumerIndex(cIndex + 1);
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        final int chunkMask = this.chunkMask;
        final long cIndex = this.lpConsumerIndex();
        final int cChunkOffset = (int) (cIndex & chunkMask);

        MpscUnboundedXaddChunk<E> cChunk = this.consumerChunk;

        // start of new chunk?
        if (cChunkOffset == 0 && cIndex !=0)
        {
            cChunk = cChunk.lvNext();
            if (cChunk == null)
            {
                return null;
            }
        }
        return cChunk.lvElement(cChunkOffset);
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
        if (null == c)
            throw new IllegalArgumentException("c is null");
        if (limit < 0)
            throw new IllegalArgumentException("limit is negative: " + limit);
        if (limit == 0)
            return 0;

        final int chunkMask = this.chunkMask;

        long cIndex = this.lpConsumerIndex();

        MpscUnboundedXaddChunk<E> cChunk = this.consumerChunk;

        for (int i = 0; i < limit; i++)
        {
            final int consumerOffset = (int) (cIndex & chunkMask);
            E e;
            if (consumerOffset == 0 && cIndex != 0)
            {
                final MpscUnboundedXaddChunk<E> next = cChunk.lvNext();
                if (next == null)
                {
                    return i;
                }
                e = next.lvElement(0);

                // if the next chunk doesn't have the first element set we give up
                if (e == null)
                {
                    return i;
                }
                moveToNextChunk(cIndex, cChunk, next);

                cChunk = next;
            }
            else
            {
                e = cChunk.lvElement(consumerOffset);
                if (e == null)
                {
                    return i;
                }
            }
            cChunk.soElement(consumerOffset, null);
            final long nextConsumerIndex = cIndex + 1;
            soConsumerIndex(nextConsumerIndex);
            c.accept(e);
            cIndex = nextConsumerIndex;
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit)
    {
        if (null == s)
            throw new IllegalArgumentException("supplier is null");
        if (limit < 0)
            throw new IllegalArgumentException("limit is negative:" + limit);
        if (limit == 0)
            return 0;

        final int chunkShift = this.chunkShift;
        final int chunkMask = this.chunkMask;

        long pIndex = getAndAddProducerIndex(limit);
        MpscUnboundedXaddChunk<E> pChunk = null;
        for (int i = 0; i < limit; i++)
        {
            final int pChunkOffset = (int) (pIndex & chunkMask);
            final long chunkIndex = pIndex >> chunkShift;
            if (pChunk == null || pChunk.lvIndex() != chunkIndex)
            {
                pChunk = producerChunkForIndex(pChunk, chunkIndex);
            }
            pChunk.soElement(pChunkOffset, s.get());
            pIndex++;
        }
        return limit;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, wait, exit);
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy w, ExitCondition exit)
    {
        MessagePassingQueueUtil.fill(this, s, w, exit);
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }
}
