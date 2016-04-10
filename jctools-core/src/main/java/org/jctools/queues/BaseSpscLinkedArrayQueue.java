package org.jctools.queues;

import java.lang.reflect.Field;
import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.UnsafeAccess.UNSAFE;

abstract class BaseSpscLinkedArrayQueuePrePad<E> extends AbstractQueue<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12;
    // p13, p14, p15, p16, p17; drop 4 longs, the cold fields act as buffer
}
abstract class BaseSpscLinkedArrayQueueProducerColdFields<E> extends BaseSpscLinkedArrayQueuePrePad<E> {
    protected int maxQueueCapacity; // ignored by the unbounded implementation
    protected int producerLookAheadStep;
    protected long producerLookAhead;
    protected long producerMask;
    protected E[] producerBuffer;
}
abstract class BaseSpscLinkedArrayQueueProducerFields<E> extends BaseSpscLinkedArrayQueueProducerColdFields<E> {
    protected long producerIndex;
}
abstract class BaseSpscLinkedArrayQueueL2Pad<E> extends BaseSpscLinkedArrayQueueProducerFields<E> {
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}
abstract class BaseSpscLinkedArrayQueueConsumerColdFields<E> extends BaseSpscLinkedArrayQueueL2Pad<E> {
    protected long consumerMask;
    protected E[] consumerBuffer;
}
abstract class BaseSpscLinkedArrayQueueConsumerField<E> extends BaseSpscLinkedArrayQueueConsumerColdFields<E> {
    protected long consumerIndex;
}

abstract class BaseSpscLinkedArrayQueue<E> extends BaseSpscLinkedArrayQueueConsumerField<E>
        implements QueueProgressIndicators {

    protected static final Object JUMP = new Object();

    private final static long P_INDEX_OFFSET;
    private final static long C_INDEX_OFFSET;
    static {
        try {
            Field iField = BaseSpscLinkedArrayQueueProducerFields.class.getDeclaredField("producerIndex");
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        try {
            Field iField = BaseSpscLinkedArrayQueueConsumerField.class.getDeclaredField("consumerIndex");
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(iField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int size() {
        /*
         * It is possible for a thread to be interrupted or rescheduled between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        while (true) {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after) {
                return (int) (currentProducerIndex - after);
            }
        }
    }

    protected void soProducerIndex(long v) {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, v);
    }

    protected void soConsumerIndex(long v) {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, v);
    }

    protected long lvProducerIndex() {
        return UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
    }

    protected long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }

    @Override
    public final Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long currentProducerIndex() {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex() {
        return lvConsumerIndex();
    }
}
