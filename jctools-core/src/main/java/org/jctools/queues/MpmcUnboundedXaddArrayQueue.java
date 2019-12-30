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

abstract class MpmcUnboundedXaddArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

// $gen:ordered-fields
abstract class MpmcUnboundedXaddArrayQueueProducerFields<E> extends MpmcUnboundedXaddArrayQueuePad1<E>
{
    private final static long P_INDEX_OFFSET =
        fieldOffset(MpmcUnboundedXaddArrayQueueProducerFields.class, "producerIndex");
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

abstract class MpmcUnboundedXaddArrayQueuePad2<E> extends MpmcUnboundedXaddArrayQueueProducerFields<E>
{
    long p01, p02, p03, p04, p05, p06, p07, p08;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpmcUnboundedXaddArrayQueueProducerChunk<E> extends MpmcUnboundedXaddArrayQueuePad2<E>
{
    private static final long P_CHUNK_OFFSET =
        fieldOffset(MpmcUnboundedXaddArrayQueueProducerChunk.class, "producerChunk");
    private static final long P_CHUNK_INDEX_OFFSET =
        fieldOffset(MpmcUnboundedXaddArrayQueueProducerChunk.class, "producerChunkIndex");

    private volatile MpmcUnboundedXaddChunk<E> producerChunk;
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

    final MpmcUnboundedXaddChunk<E> lvProducerChunk()
    {
        return this.producerChunk;
    }

    final void soProducerChunk(MpmcUnboundedXaddChunk<E> buffer)
    {
        UNSAFE.putOrderedObject(this, P_CHUNK_OFFSET, buffer);
    }
}

abstract class MpmcUnboundedXaddArrayQueuePad3<E> extends MpmcUnboundedXaddArrayQueueProducerChunk<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpmcUnboundedXaddArrayQueueConsumerFields<E> extends MpmcUnboundedXaddArrayQueuePad3<E>
{
    private final static long C_INDEX_OFFSET =
        fieldOffset(MpmcUnboundedXaddArrayQueueConsumerFields.class, "consumerIndex");
    private final static long C_CHUNK_OFFSET =
        fieldOffset(MpmcUnboundedXaddArrayQueueConsumerFields.class, "consumerChunk");

    private volatile long consumerIndex;
    private volatile MpmcUnboundedXaddChunk<E> consumerChunk;

    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    final boolean casConsumerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
    }

    final MpmcUnboundedXaddChunk<E> lvConsumerChunk()
    {
        return this.consumerChunk;
    }

    final void soConsumerBuffer(MpmcUnboundedXaddChunk<E> newValue)
    {
        UNSAFE.putOrderedObject(this, C_CHUNK_OFFSET, newValue);
    }
}

abstract class MpmcUnboundedXaddArrayQueuePad5<E> extends MpmcUnboundedXaddArrayQueueConsumerFields<E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

/**
 * An MPMC array queue which starts at <i>initialCapacity</i> and grows unbounded in linked chunks.<br>
 * Differently from {@link MpmcArrayQueue} it is designed to provide a better scaling when more
 * producers are concurrently offering.
 */
public class MpmcUnboundedXaddArrayQueue<E> extends MpmcUnboundedXaddArrayQueuePad5<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    private static final long ROTATION = -2;
    private final int chunkMask;
    private final int chunkShift;
    private final SpscArrayQueue<MpmcUnboundedXaddChunk<E>> freeChunksPool;

    public MpmcUnboundedXaddArrayQueue(int chunkSize, int maxPooledChunks)
    {
        if (!UnsafeAccess.SUPPORTS_GET_AND_ADD_LONG)
        {
            throw new IllegalStateException("Unsafe::getAndAddLong support (JDK 8+) is required for this queue to work");
        }
        chunkSize = Pow2.roundToPowerOfTwo(chunkSize);
        final MpmcUnboundedXaddChunk<E> first = new MpmcUnboundedXaddChunk(0, null, chunkSize, true);
        soProducerChunk(first);
        soProducerChunkIndex(0);
        soConsumerBuffer(first);
        chunkMask = chunkSize - 1;
        chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        freeChunksPool = new SpscArrayQueue<MpmcUnboundedXaddChunk<E>>(maxPooledChunks + 1);
        for (int i = 0; i < maxPooledChunks; i++)
        {
            freeChunksPool.offer(new MpmcUnboundedXaddChunk(MpmcUnboundedXaddChunk.CHUNK_CONSUMED, null, chunkSize, true));
        }
    }

    public MpmcUnboundedXaddArrayQueue(int chunkSize)
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

        MpmcUnboundedXaddChunk<E> pChunk = lvProducerChunk();
        if (pChunk.lvIndex() != piChunkIndex)
        {
            // Other producers may have advanced the producer chunk as we claimed a slot in a prev chunk, or we may have
            // now stepped into a brand new chunk which needs appending.
            pChunk = producerChunkForIndex(pChunk, piChunkIndex);
        }

        final boolean isPooled = pChunk.isPooled();
        // ???
        if (isPooled)
        {
            //wait any previous consumer to finish its job
            pChunk.spinForElement(piChunkOffset, true);
        }
        pChunk.soElement(piChunkOffset, e);
        if (isPooled)
        {
            pChunk.soSequence(piChunkOffset, piChunkIndex);
        }
        return true;
    }

    /**
     * We're here because currentChunk.index doesn't match the expectedChunkIndex. To resolve we must now chase the linked
     * chunks to the appropriate chunk. More than one producer may end up racing to add or discover new chunks.
     *
     * @param initialChunk the starting point chunk, which does not match the required chunk index
     * @param requiredChunkIndex the chunk index we need
     * @return the chunk matching the required index
     */
    private MpmcUnboundedXaddChunk<E> producerChunkForIndex(
        final MpmcUnboundedXaddChunk<E> initialChunk,
        final long requiredChunkIndex)
    {
        MpmcUnboundedXaddChunk<E> currentChunk = initialChunk;
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
            if (currentChunkIndex == MpmcUnboundedXaddChunk.CHUNK_CONSUMED)
            {
                //force an attempt to fetch it another time
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
                currentChunk = appendNextChunks(currentChunk, currentChunkIndex, -jumpBackward);
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

    private MpmcUnboundedXaddChunk<E> appendNextChunks(
        MpmcUnboundedXaddChunk<E> currentChunk,
        long currentChunkIndex,
        long chunksToAppend)
    {
        assert currentChunkIndex != MpmcUnboundedXaddChunk.CHUNK_CONSUMED;
        //prevent other concurrent attempts on appendNextChunk
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

            for (long i = 1; i <= chunksToAppend; i++)
            {
                MpmcUnboundedXaddChunk<E> newChunk = newChunk(currentChunk, currentChunkIndex + i);
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

    private MpmcUnboundedXaddChunk<E> newChunk(MpmcUnboundedXaddChunk<E> prevChunk, long nextChunkIndex)
    {
        MpmcUnboundedXaddChunk<E> newChunk;
        newChunk = freeChunksPool.poll();
        if (newChunk != null)
        {
            //single-writer: prevChunk::index == nextChunkIndex is protecting it
            assert newChunk.lvIndex() < prevChunk.lvIndex();
            newChunk.soPrev(prevChunk);
            //index set is releasing prev, allowing other pending offers to continue
            newChunk.soIndex(nextChunkIndex);
        }
        else
        {
            newChunk = new MpmcUnboundedXaddChunk<E>(nextChunkIndex, prevChunk, chunkMask + 1, false);
        }
        return newChunk;
    }

    @Override
    public E poll()
    {
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        long cIndex;
        MpmcUnboundedXaddChunk<E> cChunk;
        int ciChunkOffset;
        boolean isFirstElementOfNextChunk;
        boolean pooled = false;
        E e = null;
        MpmcUnboundedXaddChunk<E> next = null;
        long pIndex = -1; // start with bogus value, hope we don't need it
        long ciChunkIndex;
        while (true)
        {
            cIndex = this.lvConsumerIndex();
            // chunk is in sync with the index, and is safe to mutate after CAS of index (because we pre-verify it
            // matched the indicate ciChunkIndex)
            cChunk = this.lvConsumerChunk();

            ciChunkOffset = (int) (cIndex & chunkMask);
            ciChunkIndex = cIndex >> chunkShift;

            final long ccChunkIndex = cChunk.lvIndex();
            if (ciChunkIndex != ccChunkIndex)
            {
                // we are looking at the first element of the next chunk
                // NOTE: The check is different from the single consumer case as the MC case requires checking this
                //       is indeed the next chunk where as in the SC case this is always true.
                if (ciChunkOffset == 0 && ciChunkIndex - ccChunkIndex == 1)
                {
                    isFirstElementOfNextChunk = true;
                    next = cChunk.lvNext();
                    //next could have been modified by another racing consumer, but:
                    //- if null: it still needs to check q empty + casConsumerIndex
                    //- if !null: it will fail on casConsumerIndex
                    if (next == null)
                    {
                        if (cIndex >= pIndex && // test against cached pIndex
                            cIndex == (pIndex = lvProducerIndex())) // update pIndex if we must
                        {
                            // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                            return null;
                        }
                        // we will go ahead with the CAS and have the winning consumer spin for the next buffer
                    }
                    // not empty: can attempt the cas (and transition to next chunk if successful)
                    if (casConsumerIndex(cIndex, cIndex + 1))
                    {
                        break;
                    }
                }
                // The chunk doesn't match the consumer index view of the required chunk index. This is where consumers
                // waiting for the `next` chunk to appear will be waiting while the consumer that took the first element
                // in that chunk sets it up for them.
                continue;
            }
            isFirstElementOfNextChunk = false;
            // mid chunk elements
            pooled = cChunk.isPooled();
            if (pooled)
            {
                // Pooled chunks need a stronger guarantee than just element null checking in case of a stale view
                // on a reused entry where a racing consumer has grabbed the slot but not yet null-ed it out and a
                // producer has not yet set it to the new value.
                final long sequence = cChunk.lvSequence(ciChunkOffset);
                if (sequence != ciChunkIndex)
                {
                    //it covers both cases:
                    //- right chunk, awaiting element to be set
                    //- old chunk, awaiting rotation
                    //it allows to fail fast if the q is empty after the first element on the new chunk.
                    if (sequence < ciChunkIndex &&
                        cIndex >= pIndex && // test against cached pIndex
                        cIndex == (pIndex = lvProducerIndex())) // update pIndex if we must
                    {
                        // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                        return null;
                    }
                    //stale view of the world
                    continue;
                }
            }
            else
            {
                e = cChunk.lvElement(ciChunkOffset);
                if (e == null) // consumers do not skip unset slots
                {
                    if (cIndex >= pIndex && // test against cached pIndex
                        cIndex == (pIndex = lvProducerIndex())) // update pIndex if we must
                    {
                        // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                        return null;
                    }
                    // This is where we spin for the element to appear **before attempting the CAS**.
                    continue;
                }
            }

            if (casConsumerIndex(cIndex, cIndex + 1))
            {
                break;
            }
        }

        //if we are the isFirstElementOfNextChunk we need to get the consumer chunk
        if (isFirstElementOfNextChunk)
        {
            e = linkNextConsumerChunkAndPoll(cChunk, next, ciChunkIndex);
        }
        else
        {
            if (pooled)
            {
                e = cChunk.lvElement(ciChunkOffset);
            }
            assert !cChunk.isPooled() ||
                (cChunk.isPooled() && cChunk.lvSequence(ciChunkOffset) == ciChunkIndex);

            cChunk.soElement(ciChunkOffset, null);
        }
        return e;
    }

    private E linkNextConsumerChunkAndPoll(
        MpmcUnboundedXaddChunk<E> cChunk,
        MpmcUnboundedXaddChunk<E> next,
        long expectedChunkIndex)
    {
        while (next == null)
        {
            next = cChunk.lvNext();
        }
        //we can freely spin awaiting producer, because we are the only one in charge to
        //rotate the consumer buffer and use next
        final E e = next.spinForElement(0, false);

        final boolean pooled = next.isPooled();
        if (pooled)
        {
            while (next.lvSequence(0) != expectedChunkIndex)
            {

            }
        }
        next.soElement(0, null);

        // save from GC nepotism
        next.soPrev(null);
        cChunk.soNext(null);

        if (cChunk.isPooled())
        {
            // When is it OK to recycle? notionally only once all the consumers have consumed the elements within and
            // moved on. How do we know?
            final boolean offered = freeChunksPool.offer(cChunk);
            assert offered;
        }
        // expose next to the other consumers. Because the CAS loop is predicated on matching:
        // (ciChunkIndex == ccChunkIndex) this in effect blocks concurrent access to this method
        soConsumerBuffer(next);
        return e;
    }

    @Override
    public E peek()
    {
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        long cIndex;
        E e;
        do
        {
            e = null;
            cIndex = this.lvConsumerIndex();
            MpmcUnboundedXaddChunk<E> cChunk = this.lvConsumerChunk();
            final int ciChunkOffset = (int) (cIndex & chunkMask);
            final long ciChunkIndex = cIndex >> chunkShift;
            final boolean firstElementOfNewChunk = ciChunkOffset == 0 && cIndex != 0;
            if (firstElementOfNewChunk)
            {
                final long expectedChunkIndex = ciChunkIndex - 1;
                if (expectedChunkIndex != cChunk.lvIndex())
                {
                    continue;
                }
                final MpmcUnboundedXaddChunk<E> next = cChunk.lvNext();
                if (next == null)
                {
                    continue;
                }
                cChunk = next;
            }
            if (cChunk.isPooled())
            {
                if (cChunk.lvSequence(ciChunkOffset) != ciChunkIndex)
                {
                    continue;
                }
            } else {
                if (cChunk.lvIndex() != ciChunkIndex)
                {
                    continue;
                }
            }
            e = cChunk.lvElement(ciChunkOffset);
        }
        while (e == null && cIndex != lvProducerIndex());
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
        final int chunkShift = this.chunkShift;
        final long cIndex = this.lvConsumerIndex();
        final MpmcUnboundedXaddChunk<E> cChunk = this.lvConsumerChunk();

        final int ciChunkOffset = (int) (cIndex & chunkMask);
        final long ciChunkIndex = cIndex >> chunkShift;

        final boolean firstElementOfNewChunk = ciChunkOffset == 0 && cIndex != 0;
        if (firstElementOfNewChunk)
        {
            final long expectedChunkIndex = ciChunkIndex - 1;
            final MpmcUnboundedXaddChunk<E> next;
            final long ccChunkIndex = cChunk.lvIndex();
            if (expectedChunkIndex != ccChunkIndex || (next = cChunk.lvNext()) == null)
            {
                return null;
            }
            E e = null;
            final boolean pooled = next.isPooled();
            if (pooled)
            {
                if (next.lvSequence(ciChunkOffset) != ciChunkIndex)
                {
                    return null;
                }
            }
            else
            {
                e = next.lvElement(ciChunkOffset);
                if (e == null)
                {
                    return null;
                }
            }
            if (!casConsumerIndex(cIndex, cIndex + 1))
            {
                return null;
            }
            if (pooled)
            {
                e = next.lvElement(ciChunkOffset);
            }
            assert e != null;
            //perform the rotation
            next.soElement(ciChunkOffset, null);
            next.soPrev(null);
            // avoid GC nepotism
            cChunk.soNext(null);
            if (cChunk.isPooled())
            {
                final boolean offered = freeChunksPool.offer(cChunk);
                assert offered;
            }
            //expose next to the other consumers
            soConsumerBuffer(next);
            return e;
        }
        else
        {
            final boolean pooled = cChunk.isPooled();
            E e = null;
            if (pooled)
            {
                final long sequence = cChunk.lvSequence(ciChunkOffset);
                if (sequence != ciChunkIndex)
                {
                    return null;
                }
            }
            else
            {
                final long ccChunkIndex = cChunk.lvIndex();
                if (ccChunkIndex != ciChunkIndex || (e = cChunk.lvElement(ciChunkOffset)) == null)
                {
                    return null;
                }
            }
            if (!casConsumerIndex(cIndex, cIndex + 1))
            {
                return null;
            }
            if (pooled)
            {
                e = cChunk.lvElement(ciChunkOffset);
                assert e != null;
            }
            assert !pooled ||
                (pooled && cChunk.lvSequence(ciChunkOffset) == ciChunkIndex);
            cChunk.soElement(ciChunkOffset, null);
            return e;
        }
    }

    @Override
    public E relaxedPeek()
    {
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        final long cIndex = this.lvConsumerIndex();
        final int ciChunkOffset = (int) (cIndex & chunkMask);
        final long ciChunkIndex = cIndex >> chunkShift;

        MpmcUnboundedXaddChunk<E> consumerBuffer = this.lvConsumerChunk();

        final int chunkSize = chunkMask + 1;
        final boolean firstElementOfNewChunk = ciChunkOffset == 0 && cIndex >= chunkSize;
        if (firstElementOfNewChunk)
        {
            final long expectedChunkIndex = ciChunkIndex - 1;
            if (expectedChunkIndex != consumerBuffer.lvIndex())
            {
                return null;
            }
            final MpmcUnboundedXaddChunk<E> next = consumerBuffer.lvNext();
            if (next == null)
            {
                return null;
            }
            consumerBuffer = next;
        }
        if (consumerBuffer.isPooled())
        {
            if (consumerBuffer.lvSequence(ciChunkOffset) != ciChunkIndex)
            {
                return null;
            }
        }
        else
        {
            if (consumerBuffer.lvIndex() != ciChunkIndex)
            {
                return null;
            }
        }
        return consumerBuffer.lvElement(ciChunkOffset);
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return MessagePassingQueueUtil.drain(this, c);
    }

    @Override
    public int fill(Supplier<E> s)
    {
        final int chunkCapacity = chunkMask + 1;
        final int offerBatch = Math.min(PortableJvmInfo.RECOMENDED_OFFER_BATCH, chunkCapacity);
        return MessagePassingQueueUtil.fillInBatchesToLimit(this, s, offerBatch, chunkCapacity);
    }

    @Override
    public int drain(Consumer<E> c, int limit)
    {
        return MessagePassingQueueUtil.drain(this, c, limit);
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
        long producerSeq = getAndAddProducerIndex(limit);
        MpmcUnboundedXaddChunk<E> producerBuffer = null;
        for (int i = 0; i < limit; i++)
        {
            final int pOffset = (int) (producerSeq & chunkMask);
            long chunkIndex = producerSeq >> chunkShift;
            if (producerBuffer == null || producerBuffer.lvIndex() != chunkIndex)
            {
                producerBuffer = producerChunkForIndex(producerBuffer, chunkIndex);
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
        MessagePassingQueueUtil.fill(this, s, wait, exit);
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

}
