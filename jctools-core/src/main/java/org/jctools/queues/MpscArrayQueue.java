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

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;
import static org.jctools.util.UnsafeRefArrayAccess.spElement;

import org.jctools.queues.MessagePassingQueue.Consumer;
import org.jctools.queues.MessagePassingQueue.ExitCondition;
import org.jctools.queues.MessagePassingQueue.Supplier;
import org.jctools.queues.MessagePassingQueue.WaitStrategy;
import org.jctools.util.UnsafeRefArrayAccess;

abstract class MpscArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16;
    public MpscArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscArrayQueueTailField<E> extends MpscArrayQueueL1Pad<E> {
    private final static long P_INDEX_OFFSET;

    static {
        try {
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(MpscArrayQueueTailField.class
                    .getDeclaredField("producerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long producerIndex;

    public MpscArrayQueueTailField(int capacity) {
        super(capacity);
    }

    protected final long lvProducerIndex() {
        return producerIndex;
    }

    protected final boolean casProducerIndex(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpscArrayQueueMidPad<E> extends MpscArrayQueueTailField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    public MpscArrayQueueMidPad(int capacity) {
        super(capacity);
    }
}

abstract class MpscArrayQueueHeadCacheField<E> extends MpscArrayQueueMidPad<E> {
    private volatile long consumerIndexCache;

    public MpscArrayQueueHeadCacheField(int capacity) {
        super(capacity);
    }

    protected final long lvConsumerIndexCache() {
        return consumerIndexCache;
    }

    protected final void svConsumerIndexCache(long v) {
        consumerIndexCache = v;
    }
}

abstract class MpscArrayQueueL2Pad<E> extends MpscArrayQueueHeadCacheField<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16;
    public MpscArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscArrayQueueConsumerField<E> extends MpscArrayQueueL2Pad<E> {
    private final static long C_INDEX_OFFSET;
    static {
        try {
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(MpscArrayQueueConsumerField.class
                    .getDeclaredField("consumerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    protected long consumerIndex;

    public MpscArrayQueueConsumerField(int capacity) {
        super(capacity);
    }

    protected final long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }

    protected void soConsumerIndex(long l) {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, l);
    }
}

/**
 * A Multi-Producer-Single-Consumer queue based on a {@link ConcurrentCircularArrayQueue}. This implies that
 * any thread may call the offer method, but only a single thread may call poll/peek for correctness to
 * maintained. <br>
 * This implementation follows patterns documented on the package level for False Sharing protection.<br>
 * This implementation is using the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * method for polling from the queue (with minor change to correctly publish the index) and an extension of
 * the Leslie Lamport concurrent queue algorithm (originated by Martin Thompson) on the producer side.<br>
 * 
 * @author nitsanw
 * 
 * @param <E>
 */
public class MpscArrayQueue<E> extends MpscArrayQueueConsumerField<E> implements QueueProgressIndicators {
    private final static int RECOMENDED_OFFER_BATCH = Runtime.getRuntime().availableProcessors() * 4;
    
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    public MpscArrayQueue(final int capacity) {
        super(capacity);
    }

    /**
     * {@inheritDoc} <br>
     * 
     * IMPLEMENTATION NOTES:<br>
     * Lock free offer using a single CAS. As class name suggests access is permitted to many threads
     * concurrently.
     * 
     * @see java.util.Queue#offer(java.lang.Object)
     * @see MessagePassingQueue#offer(Object)
     */
    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        // use a cached view on consumer index (potentially updated in loop)
        final long mask = this.mask;
        final long capacity = mask + 1;
        long consumerIndexCache = lvConsumerIndexCache(); // LoadLoad
        long currentProducerIndex;
        do {
            currentProducerIndex = lvProducerIndex(); // LoadLoad
            final long wrapPoint = currentProducerIndex - capacity;
            if (consumerIndexCache <= wrapPoint) {
                final long currHead = lvConsumerIndex(); // LoadLoad
                if (currHead <= wrapPoint) {
                    return false; // FULL :(
                } else {
                    // update shared cached value of the consumerIndex
                    svConsumerIndexCache(currHead); // StoreLoad
                    // update on stack copy, we might need this value again if we lose the CAS.
                    consumerIndexCache = currHead;
                }
            }
        } while (!casProducerIndex(currentProducerIndex, currentProducerIndex + 1));
        /*
         * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
         * the index visibility to poll() we would need to handle the case where the element is not visible.
         */

        // Won CAS, move on to storing
        final long offset = calcElementOffset(currentProducerIndex, mask);
        UnsafeRefArrayAccess.soElement(buffer, offset, e); // StoreStore
        return true; // AWESOME :)
    }

    /**
     * A wait free alternative to offer which fails on CAS failure.
     * 
     * @param e new element, not null
     * @return 1 if next element cannot be filled, -1 if CAS failed, 0 if successful
     */
    public final int failFastOffer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        final long mask = this.mask;
        final long capacity = mask + 1;
        final long currentTail = lvProducerIndex(); // LoadLoad
        final long consumerIndexCache = lvConsumerIndexCache(); // LoadLoad
        final long wrapPoint = currentTail - capacity;
        if (consumerIndexCache <= wrapPoint) {
            long currHead = lvConsumerIndex(); // LoadLoad
            if (currHead <= wrapPoint) {
                return 1; // FULL :(
            } else {
                svConsumerIndexCache(currHead); // StoreLoad
            }
        }

        // look Ma, no loop!
        if (!casProducerIndex(currentTail, currentTail + 1)) {
            return -1; // CAS FAIL :(
        }

        // Won CAS, move on to storing
        final long offset = calcElementOffset(currentTail, mask);
        soElement(buffer, offset, e);
        return 0; // AWESOME :)
    }

    /**
     * {@inheritDoc}
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free poll using ordered loads/stores. As class name suggests access is limited to a single thread.
     * 
     * @see java.util.Queue#poll()
     * @see MessagePassingQueue#poll()
     */
    @Override
    public E poll() {
        final long consumerIndex = lvConsumerIndex(); // LoadLoad
        final long offset = calcElementOffset(consumerIndex);
        // Copy field to avoid re-reading after volatile load
        final E[] buffer = this.buffer;

        // If we can't see the next available element we can't poll
        E e = lvElement(buffer, offset); // LoadLoad
        if (null == e) {
            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (consumerIndex != lvProducerIndex()) {
                do {
                    e = lvElement(buffer, offset);
                } while (e == null);
            } else {
                return null;
            }
        }

        spElement(buffer, offset, null);
        soConsumerIndex(consumerIndex + 1); // StoreStore
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free peek using ordered loads. As class name suggests access is limited to a single thread.
     * 
     * @see java.util.Queue#poll()
     * @see MessagePassingQueue#poll()
     */
    @Override
    public E peek() {
        // Copy field to avoid re-reading after volatile load
        final E[] buffer = this.buffer;

        final long consumerIndex = lvConsumerIndex(); // LoadLoad
        final long offset = calcElementOffset(consumerIndex);
        E e = lvElement(buffer, offset);
        if (null == e) {
            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (consumerIndex != lvProducerIndex()) {
                do {
                    e = lvElement(buffer, offset);
                } while (e == null);
            } else {
                return null;
            }
        }
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 
     */
    @Override
    public int size() {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
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

    @Override
    public boolean isEmpty() {
        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures the correctness of this method at least for the consumer thread. Other threads POV is
        // not really
        // something we can fix here.
        return (lvConsumerIndex() == lvProducerIndex());
    }
    
    @Override
    public long currentProducerIndex() {
        return lvProducerIndex();
    }
    
    @Override
    public long currentConsumerIndex() {
        return lvConsumerIndex();
    }
    
	@Override
	public boolean relaxedOffer(E e) {
        return offer(e);
	}

	@Override
	public E relaxedPoll() {
		final E[] buffer = this.buffer;
		final long consumerIndex = this.consumerIndex; // LoadLoad
        final long offset = calcElementOffset(consumerIndex);

        // If we can't see the next available element we can't poll
        E e = lvElement(buffer, offset); // LoadLoad
        if (null == e) {
            return null;
        }

        spElement(buffer, offset, null);
        soConsumerIndex(consumerIndex + 1); // StoreStore
        return e;
    }

    @Override
    public E relaxedPeek() {
    	final E[] buffer = this.buffer;
		final long mask = this.mask;
        final long consumerIndex = lvConsumerIndex();
        return lvElement(buffer, calcElementOffset(consumerIndex, mask));
	}

    @Override
    public int drain(Consumer<E> c) {
        return drain(c, capacity());
    }

    @Override
    public int fill(Supplier<E> s) {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        final int capacity = capacity();
        do {
            final int filled = fill(s, RECOMENDED_OFFER_BATCH);
            if (filled == 0) {
                return (int) result;
            }
            result += filled;
        } while (result <= capacity);
        return (int) result;
    }


    @Override
    public int drain(final Consumer<E> c, final int limit) {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long consumerIndex = this.consumerIndex;

        for (int i = 0; i < limit; i++) {
            final long index = consumerIndex + i;
            final long offset = calcElementOffset(index, mask);
            final E e = lvElement(buffer, offset);// LoadLoad
            if (null == e) {
                return i;
            }
            soElement(buffer, offset, null);// StoreStore
            soConsumerIndex(index + 1); // ordered store -> atomic and ordered for size()
            c.accept(e);
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit) {
        final long mask = this.mask;
        final long capacity = mask + 1;
        long cachedConsumerIndex = lvConsumerIndexCache(); // LoadLoad
        long currentProducerIndex;
        int actualLimit = 0;
        do {
            currentProducerIndex = lvProducerIndex(); // LoadLoad
            long available = capacity - (currentProducerIndex - cachedConsumerIndex);
            if (available <= 0) {
                final long currConsumerIndex = lvConsumerIndex(); // LoadLoad
                available += currConsumerIndex - cachedConsumerIndex;
                if (available <= 0) {
                    return 0; // FULL :(
                } else {
                    // update shared cached value of the consumerIndex
                    svConsumerIndexCache(currConsumerIndex); // StoreLoad
                    // update on stack copy, we might need this value again if we lose the CAS.
                    cachedConsumerIndex = currConsumerIndex;
                }
            }
            actualLimit = Math.min((int)available, limit);
        } while (!casProducerIndex(currentProducerIndex, currentProducerIndex + actualLimit));
        // right, now we claimed a few slots and can fill them with goodness
        final E[] buffer = this.buffer;
        for (int i = 0;i < actualLimit;i++) {
            // Won CAS, move on to storing
            final long offset = calcElementOffset(currentProducerIndex + i, mask);
            soElement(buffer, offset, s.get());
        }
        return actualLimit;
    }

    @Override
    public void drain(Consumer<E> c,
            WaitStrategy w,
            ExitCondition exit) {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        long consumerIndex = this.consumerIndex;

        int counter = 0;
        while (exit.keepRunning()) {
            for (int i = 0; i < 4096; i++) {
                final long offset = calcElementOffset(consumerIndex, mask);
                final E e = lvElement(buffer, offset);// LoadLoad
                if (null == e) {
                    counter = w.idle(counter);
                    continue;
                }
                consumerIndex++;
                counter = 0;
                soElement(buffer, offset, null);// StoreStore
                soConsumerIndex(consumerIndex); // ordered store -> atomic and ordered for size()
                c.accept(e);
            }
        }
    }

    @Override
    public void fill(Supplier<E> s,
            WaitStrategy wait,
            ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            if (fill(s, RECOMENDED_OFFER_BATCH) == 0) {
                idleCounter = wait.idle(idleCounter);
                continue;
            }
            idleCounter = 0;    
        }
    }
}
