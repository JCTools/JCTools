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


/**
 * An MPMC array queue which starts at <i>initialCapacity</i> and grows unbounded in linked chunks.<br>
 * Differently from {@link MpmcArrayQueue} it is designed to provide a better scaling when more
 * producers are concurrently offering.
 *
 * @author https://github.com/franz1981
 */
public class MpmcUnboundedXaddArrayQueue<E> extends MpUnboundedXaddArrayQueue<MpmcUnboundedXaddChunk<E>, E>
{

    /**
     * @param chunkSize The buffer size to be used in each chunk of this queue
     * @param maxPooledChunks The maximum number of reused chunks kept around to avoid allocation, chunks are pre-allocated
     */
    public MpmcUnboundedXaddArrayQueue(int chunkSize, int maxPooledChunks)
    {
        super(chunkSize, maxPooledChunks);
    }

    public MpmcUnboundedXaddArrayQueue(int chunkSize)
    {
        this(chunkSize, 2);
    }

    @Override
    final MpmcUnboundedXaddChunk<E> newChunk(long index, MpmcUnboundedXaddChunk<E> prev, int chunkSize, boolean pooled)
    {
        return new MpmcUnboundedXaddChunk(index, prev, chunkSize, pooled);
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

        if (isPooled)
        {
            // wait any previous consumer to finish its job
            pChunk.spinForElement(piChunkOffset, true);
        }
        pChunk.soElement(piChunkOffset, e);
        if (isPooled)
        {
            pChunk.soSequence(piChunkOffset, piChunkIndex);
        }
        return true;
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
                if (ciChunkOffset == 0 && ciChunkIndex - ccChunkIndex == 1)
                {
                    isFirstElementOfNextChunk = true;
                    next = cChunk.lvNext();
                    // next could have been modified by another racing consumer, but:
                    // - if null: it still needs to check q empty + casConsumerIndex
                    // - if !null: it will fail on casConsumerIndex
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
                } else if (ccChunkIndex < ciChunkIndex) {
                    // it allows to fail fast if the q is empty after the first element on the new chunk:
                    // it can happen if consumerIndex is being already moved forward, but
                    // the consumerChunk isn't yet rotated
                    if (cIndex >= pIndex && // test against cached pIndex
                        cIndex == (pIndex = lvProducerIndex())) // update pIndex if we must
                    {
                        // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                        return null;
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
                    // it covers both cases:
                    // - right chunk, awaiting element to be set
                    // - old chunk, awaiting rotation
                    // it allows to fail fast if the q is empty after the first element on the new chunk
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

        // if we are the isFirstElementOfNextChunk we need to get the consumer chunk
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
            assert !cChunk.isPooled() ||  (cChunk.isPooled() && cChunk.lvSequence(ciChunkOffset) == ciChunkIndex);

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
        // we can freely spin awaiting producer, because we are the only one in charge to
        // rotate the consumer buffer and use next
        final E e = next.spinForElement(0, false);

        final boolean pooled = next.isPooled();
        if (pooled)
        {
            while (next.lvSequence(0) != expectedChunkIndex)
            {

            }
        }

        next.soElement(0, null);
        moveToNextConsumerChunk(cChunk, next);
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

            next.soElement(0, null);
            moveToNextConsumerChunk(cChunk, next);
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
            assert !pooled || (pooled && cChunk.lvSequence(ciChunkOffset) == ciChunkIndex);
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

}
