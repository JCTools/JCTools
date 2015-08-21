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

import org.jctools.util.UnsafeRefArrayAccess;

abstract class SpmcArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpmcArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpmcArrayQueueProducerField<E> extends SpmcArrayQueueL1Pad<E> {
    protected final static long P_INDEX_OFFSET;
    static {
        try {
            P_INDEX_OFFSET =
                    UNSAFE.objectFieldOffset(SpmcArrayQueueProducerField.class.getDeclaredField("producerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long producerIndex;

    protected final long lvProducerIndex() {
        return producerIndex;
    }

    protected final void soProducerIndex(long v) {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, v);
    }

    public SpmcArrayQueueProducerField(int capacity) {
        super(capacity);
    }
}

abstract class SpmcArrayQueueL2Pad<E> extends SpmcArrayQueueProducerField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpmcArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpmcArrayQueueConsumerField<E> extends SpmcArrayQueueL2Pad<E> {
    protected final static long C_INDEX_OFFSET;
    static {
        try {
            C_INDEX_OFFSET =
                    UNSAFE.objectFieldOffset(SpmcArrayQueueConsumerField.class.getDeclaredField("consumerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long consumerIndex;

    public SpmcArrayQueueConsumerField(int capacity) {
        super(capacity);
    }

    protected final long lvConsumerIndex() {
        return consumerIndex;
    }

    protected final boolean casHead(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
    }
}

abstract class SpmcArrayQueueMidPad<E> extends SpmcArrayQueueConsumerField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpmcArrayQueueMidPad(int capacity) {
        super(capacity);
    }
}

abstract class SpmcArrayQueueProducerIndexCacheField<E> extends SpmcArrayQueueMidPad<E> {
    // This is separated from the consumerIndex which will be highly contended in the hope that this value spends most
    // of it's time in a cache line that is Shared(and rarely invalidated)
    private volatile long producerIndexCache;

    public SpmcArrayQueueProducerIndexCacheField(int capacity) {
        super(capacity);
    }

    protected final long lvProducerIndexCache() {
        return producerIndexCache;
    }

    protected final void svProducerIndexCache(long v) {
        producerIndexCache = v;
    }
}

abstract class SpmcArrayQueueL3Pad<E> extends SpmcArrayQueueProducerIndexCacheField<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpmcArrayQueueL3Pad(int capacity) {
        super(capacity);
    }
}

public class SpmcArrayQueue<E> extends SpmcArrayQueueL3Pad<E> implements QueueProgressIndicators {
    public SpmcArrayQueue(final int capacity) {
        super(capacity);
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long currProducerIndex = lvProducerIndex();
        final long offset = calcElementOffset(currProducerIndex, mask);
        if (null != UnsafeRefArrayAccess.lvElement(buffer, offset)) {
            long size = currProducerIndex - lvConsumerIndex();
            
            if(size > mask) {
                return false;
            }
            else {
                // spin wait for slot to clear, buggers wait freedom
                while(null != UnsafeRefArrayAccess.lvElement(buffer, offset));
            }
        }
        UnsafeRefArrayAccess.spElement(buffer, offset, e);
        // single producer, so store ordered is valid. It is also required to correctly publish the element
        // and for the consumers to pick up the tail value.
        soProducerIndex(currProducerIndex + 1);
        return true;
    }

    @Override
    public E poll() {
        long currentConsumerIndex;
        final long currProducerIndexCache = lvProducerIndexCache();
        do {
            currentConsumerIndex = lvConsumerIndex();
            if (currentConsumerIndex >= currProducerIndexCache) {
                long currProducerIndex = lvProducerIndex();
                if (currentConsumerIndex >= currProducerIndex) {
                    return null;
                } else {
                    svProducerIndexCache(currProducerIndex);
                }
            }
        } while (!casHead(currentConsumerIndex, currentConsumerIndex + 1));
        // consumers are gated on latest visible tail, and so can't see a null value in the queue or overtake
        // and wrap to hit same location.
        final long offset = calcElementOffset(currentConsumerIndex);
        final E[] lb = buffer;
        // load plain, element happens before it's index becomes visible
        final E e = UnsafeRefArrayAccess.lpElement(lb, offset);
        // store ordered, make sure nulling out is visible. Producer is waiting for this value.
        UnsafeRefArrayAccess.soElement(lb, offset, null);
        return e;
    }

    @Override
    public E peek() {
        final long mask = this.mask;
        final long currProducerIndexCache = lvProducerIndexCache();
        long currentConsumerIndex;
        E e;
        do {
            currentConsumerIndex = lvConsumerIndex();
            if (currentConsumerIndex >= currProducerIndexCache) {
                long currProducerIndex = lvProducerIndex();
                if (currentConsumerIndex >= currProducerIndex) {
                    return null;
                } else {
                    svProducerIndexCache(currProducerIndex);
                }
            }
        } while (null == (e = UnsafeRefArrayAccess.lvElement(buffer, calcElementOffset(currentConsumerIndex, mask))));
        return e;
    }

    @Override
    public int size() {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and consumer
         * indices, therefore protection is required to ensure size is within valid range. In the event of concurrent
         * polls/offers to this method the size is OVER estimated as we read consumer index BEFORE the producer index.
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
        // This ensures the correctness of this method at least for the consumer thread. Other threads POV is not really
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
		if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long producerIndex = lvProducerIndex();
        final long offset = calcElementOffset(producerIndex, mask);
        if (null != UnsafeRefArrayAccess.lvElement(buffer, offset)) {
        	return false;
        }
        UnsafeRefArrayAccess.spElement(buffer, offset, e);
        // single producer, so store ordered is valid. It is also required to correctly publish the element
        // and for the consumers to pick up the tail value.
        soProducerIndex(producerIndex + 1);
        return true;
	}

	@Override
	public E relaxedPoll() {
        return poll();
    }

    @Override
    public E relaxedPeek() {
    	final E[] buffer = this.buffer;
		final long mask = this.mask;
        final long consumerIndex = lvConsumerIndex();
        return lvElement(buffer, calcElementOffset(consumerIndex, mask));
	}
}
