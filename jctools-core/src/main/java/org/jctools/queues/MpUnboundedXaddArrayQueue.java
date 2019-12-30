package org.jctools.queues;

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.queues.MpUnboundedXaddChunk.CHUNK_CONSUMED;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

abstract class MpUnboundedXaddArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

// $gen:ordered-fields
abstract class MpUnboundedXaddArrayQueueProducerFields<E> extends MpUnboundedXaddArrayQueuePad1<E>
{
    private final static long P_INDEX_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueProducerFields.class, "producerIndex");
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

abstract class MpUnboundedXaddArrayQueuePad2<E> extends MpUnboundedXaddArrayQueueProducerFields<E>
{
    long p01, p02, p03, p04, p05, p06, p07, p08;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpUnboundedXaddArrayQueueProducerChunk<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddArrayQueuePad2<E>
{
    private static final long P_CHUNK_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueProducerChunk.class, "producerChunk");
    private static final long P_CHUNK_INDEX_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueProducerChunk.class, "producerChunkIndex");

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

abstract class MpUnboundedXaddArrayQueuePad3<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddArrayQueueProducerChunk<R, E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

// $gen:ordered-fields
abstract class MpUnboundedXaddArrayQueueConsumerFields<R extends MpUnboundedXaddChunk<R, E>, E>
    extends MpUnboundedXaddArrayQueuePad3<R, E>
{
    private final static long C_INDEX_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueConsumerFields.class, "consumerIndex");
    private final static long C_CHUNK_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueConsumerFields.class, "consumerChunk");

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

abstract class MpUnboundedXaddArrayQueuePad5<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddArrayQueueConsumerFields<R, E>
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16;
}

/**
 * Common infrastructure for the XADD queues.
 *
 * @author https://github.com/franz1981
 */
abstract class MpUnboundedXaddArrayQueue<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddArrayQueuePad5<R, E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    private static final long ROTATION = -2;
    final int chunkMask;
    final int chunkShift;
    final SpscArrayQueue<R> freeChunksPool;

    public MpUnboundedXaddArrayQueue(int chunkSize, int maxPooledChunks)
    {
        if (!UnsafeAccess.SUPPORTS_GET_AND_ADD_LONG)
        {
            throw new IllegalStateException("Unsafe::getAndAddLong support (JDK 8+) is required for this queue to work");
        }

        this.chunkMask = chunkSize - 1;
        this.chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        freeChunksPool = new SpscArrayQueue<R>(maxPooledChunks + 1);

        chunkSize = Pow2.roundToPowerOfTwo(chunkSize);
        final R first = newChunk(0, null, chunkSize, true);
        soProducerChunk(first);
        soProducerChunkIndex(0);
        soConsumerChunk(first);
        for (int i = 0; i < maxPooledChunks; i++)
        {
            freeChunksPool.offer(newChunk(CHUNK_CONSUMED, null, chunkSize, true));
        }
    }

    protected abstract R newChunk(long index, R prev, int chunkSize, boolean pooled);

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
    protected R producerChunkForIndex(
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
            // Consumer will set the chunk index to CHUNK_CONSUMED when it is consumed, we should only see this case
            // if the consumer has done so concurrent to our attempts to use it.
            if (currentChunkIndex == CHUNK_CONSUMED)
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
            currentChunk = (R) currentChunk.lvPrev();
            assert currentChunk != null;
        }
        assert currentChunk.lvIndex() == requiredChunkIndex;
        return currentChunk;
    }

    private R appendNextChunks(
        R currentChunk,
        long currentChunkIndex,
        long chunksToAppend)
    {
        assert currentChunkIndex != CHUNK_CONSUMED;
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
            assert ((lvIndex = currentChunk.lvIndex()) == CHUNK_CONSUMED ||
                currentChunkIndex == lvIndex);

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
        R newChunk;
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
            newChunk = newChunk(nextChunkIndex, prevChunk, chunkMask + 1, false);
        }
        return newChunk;
    }


    protected R pollNextBuffer(R cChunk, long cIndex)
    {
        final R next = spinForNextIfNotEmpty(cChunk, cIndex);

        if (next == null)
        {
            return null;
        }

        moveToNextConsumerChunk(cChunk, next);
        assert next.lvIndex() == cIndex >> chunkShift;
        return next;
    }

    protected R spinForNextIfNotEmpty(R cChunk, long cIndex)
    {
        R next = cChunk.lvNext();
        if (next == null)
        {
            if (lvProducerIndex() == cIndex)
            {
                return null;
            }

            do
            {
                next = cChunk.lvNext();
            }
            while (next == null);
        }
        return next;
    }

    /**
     * Does not null out the first element of `next`, callers must do that
     */
    protected void moveToNextConsumerChunk(R cChunk, R next)
    {
        // avoid GC nepotism
        cChunk.soNext(null);// change the chunkIndex to a non valid value to stop offering threads to use this chunk
        next.soPrev(null);
        cChunk.soIndex(CHUNK_CONSUMED);
        if (cChunk.isPooled())
        {
            final boolean pooled = freeChunksPool.offer(cChunk);
            assert pooled;
        }
        this.soConsumerChunk(next);
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
