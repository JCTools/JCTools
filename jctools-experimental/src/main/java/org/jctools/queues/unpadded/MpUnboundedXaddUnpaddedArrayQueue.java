package org.jctools.queues.unpadded;

import org.jctools.queues.*;
import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.queues.MpUnboundedXaddChunk.NOT_USED;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

abstract class MpUnboundedXaddUnpaddedArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{

}

// $gen:ordered-fields
abstract class MpUnboundedXaddUnpaddedArrayQueueProducerFields<E> extends MpUnboundedXaddUnpaddedArrayQueuePad1<E>
{
    private final static long P_INDEX_OFFSET =
        fieldOffset(MpUnboundedXaddUnpaddedArrayQueueProducerFields.class, "producerIndex");
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

abstract class MpUnboundedXaddUnpaddedArrayQueuePad2<E> extends MpUnboundedXaddUnpaddedArrayQueueProducerFields<E>
{

}

// $gen:ordered-fields
abstract class MpUnboundedXaddUnpaddedArrayQueueProducerChunk<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddUnpaddedArrayQueuePad2<E>
{
    private static final long P_CHUNK_OFFSET =
        fieldOffset(MpUnboundedXaddUnpaddedArrayQueueProducerChunk.class, "producerChunk");
    private static final long P_CHUNK_INDEX_OFFSET =
        fieldOffset(MpUnboundedXaddUnpaddedArrayQueueProducerChunk.class, "producerChunkIndex");

    private volatile R producerChunk;
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

    final R lvProducerChunk()
    {
        return this.producerChunk;
    }

    final void soProducerChunk(R chunk)
    {
        UNSAFE.putOrderedObject(this, P_CHUNK_OFFSET, chunk);
    }
}

abstract class MpUnboundedXaddUnpaddedArrayQueuePad3<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddUnpaddedArrayQueueProducerChunk<R, E>
{

}

// $gen:ordered-fields
abstract class MpUnboundedXaddUnpaddedArrayQueueConsumerFields<R extends MpUnboundedXaddChunk<R, E>, E>
    extends MpUnboundedXaddUnpaddedArrayQueuePad3<R, E>
{
    private final static long C_INDEX_OFFSET =
        fieldOffset(MpUnboundedXaddUnpaddedArrayQueueConsumerFields.class, "consumerIndex");
    private final static long C_CHUNK_OFFSET =
        fieldOffset(MpUnboundedXaddUnpaddedArrayQueueConsumerFields.class, "consumerChunk");

    private volatile long consumerIndex;
    private volatile R consumerChunk;

    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    final boolean casConsumerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
    }

    final R lpConsumerChunk()
    {
        return (R) UNSAFE.getObject(this, C_CHUNK_OFFSET);
    }

    final R lvConsumerChunk()
    {
        return this.consumerChunk;
    }

    final void soConsumerChunk(R newValue)
    {
        UNSAFE.putOrderedObject(this, C_CHUNK_OFFSET, newValue);
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

abstract class MpUnboundedXaddUnpaddedArrayQueuePad5<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddUnpaddedArrayQueueConsumerFields<R, E>
{

}

/**
 * Common infrastructure for the XADD queues.
 *
 * @author https://github.com/franz1981
 */
abstract class MpUnboundedXaddUnpaddedArrayQueue<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddUnpaddedArrayQueuePad5<R, E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    // it must be != MpUnboundedXaddChunk.NOT_USED
    private static final long ROTATION = -2;
    final int chunkMask;
    final int chunkShift;
    final int maxPooledChunks;
    final SpscUnpaddedArrayQueue<R> freeChunksPool;

    /**
     * @param chunkSize The buffer size to be used in each chunk of this queue
     * @param maxPooledChunks The maximum number of reused chunks kept around to avoid allocation, chunks are pre-allocated
     */
    MpUnboundedXaddUnpaddedArrayQueue(int chunkSize, int maxPooledChunks)
    {
        if (!UnsafeAccess.SUPPORTS_GET_AND_ADD_LONG)
        {
            throw new IllegalStateException("Unsafe::getAndAddLong support (JDK 8+) is required for this queue to work");
        }
        if (maxPooledChunks < 0)
        {
            throw new IllegalArgumentException("Expecting a positive maxPooledChunks, but got:"+maxPooledChunks);
        }
        chunkSize = Pow2.roundToPowerOfTwo(chunkSize);

        this.chunkMask = chunkSize - 1;
        this.chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        freeChunksPool = new SpscUnpaddedArrayQueue<R>(maxPooledChunks);

        final R first = newChunk(0, null, chunkSize, maxPooledChunks > 0);
        soProducerChunk(first);
        soProducerChunkIndex(0);
        soConsumerChunk(first);
        for (int i = 1; i < maxPooledChunks; i++)
        {
            freeChunksPool.offer(newChunk(NOT_USED, null, chunkSize, true));
        }
        this.maxPooledChunks = maxPooledChunks;
    }

    public final int chunkSize()
    {
        return chunkMask + 1;
    }

    public final int maxPooledChunks()
    {
        return maxPooledChunks;
    }

    abstract R newChunk(long index, R prev, int chunkSize, boolean pooled);

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

    /**
     * We're here because currentChunk.index doesn't match the expectedChunkIndex. To resolve we must now chase the linked
     * chunks to the appropriate chunk. More than one producer may end up racing to add or discover new chunks.
     *
     * @param initialChunk the starting point chunk, which does not match the required chunk index
     * @param requiredChunkIndex the chunk index we need
     * @return the chunk matching the required index
     */
    final R producerChunkForIndex(
        final R initialChunk,
        final long requiredChunkIndex)
    {
        R currentChunk = initialChunk;
        long jumpBackward;
        while (true)
        {
            if (currentChunk == null)
            {
                currentChunk = lvProducerChunk();
            }
            final long currentChunkIndex = currentChunk.lvIndex();
            assert currentChunkIndex != NOT_USED;
            // if the required chunk index is less than the current chunk index then we need to walk the linked list of
            // chunks back to the required index
            jumpBackward = currentChunkIndex - requiredChunkIndex;
            if (jumpBackward >= 0)
            {
                break;
            }
            // try validate against the last producer chunk index
            if (lvProducerChunkIndex() == currentChunkIndex)
            {
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
            currentChunk = currentChunk.lvPrev();
            assert currentChunk != null;
        }
        assert currentChunk.lvIndex() == requiredChunkIndex;
        return currentChunk;
    }

    protected final R appendNextChunks(
        R currentChunk,
        long currentChunkIndex,
        long chunksToAppend)
    {
        assert currentChunkIndex != NOT_USED;
        // prevent other concurrent attempts on appendNextChunk
        if (!casProducerChunkIndex(currentChunkIndex, ROTATION))
        {
            return null;
        }
        /* LOCKED FOR APPEND */
        {
            // it is valid for the currentChunk to be consumed while appending is in flight, but it's not valid for the
            // current chunk ordering to change otherwise.
            assert currentChunkIndex == currentChunk.lvIndex();

            for (long i = 1; i <= chunksToAppend; i++)
            {
                R newChunk = newOrPooledChunk(currentChunk, currentChunkIndex + i);
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

    private R newOrPooledChunk(R prevChunk, long nextChunkIndex)
    {
        R newChunk = freeChunksPool.poll();
        if (newChunk != null)
        {
            // single-writer: prevChunk::index == nextChunkIndex is protecting it
            assert newChunk.lvIndex() < prevChunk.lvIndex();
            newChunk.soPrev(prevChunk);
            // index set is releasing prev, allowing other pending offers to continue
            newChunk.soIndex(nextChunkIndex);
        }
        else
        {
            newChunk = newChunk(nextChunkIndex, prevChunk, chunkMask + 1, false);
        }
        return newChunk;
    }


    /**
     * Does not null out the first element of `next`, callers must do that
     */
    final void moveToNextConsumerChunk(R cChunk, R next)
    {
        // avoid GC nepotism
        cChunk.soNext(null);
        next.soPrev(null);
        // no need to cChunk.soIndex(NOT_USED)
        if (cChunk.isPooled())
        {
            final boolean pooled = freeChunksPool.offer(cChunk);
            assert pooled;
        }
        this.soConsumerChunk(next);
        // MC case:
        // from now on the code is not single-threaded anymore and
        // other consumers can move forward consumerIndex
    }

    @Override
    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size()
    {
        return IndexedQueueSizeUtil.size(this, IndexedQueueSizeUtil.PLAIN_DIVISOR);
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
