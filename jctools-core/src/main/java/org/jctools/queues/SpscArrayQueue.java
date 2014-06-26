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


abstract class SpscArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E> {
    long p10, p11, p12, p13, p14, p15, p16;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpscArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpscArrayQueueProducerFields<E> extends SpscArrayQueueL1Pad<E> {
    protected long producerIndex;
    protected long producerLookAhead;

    public SpscArrayQueueProducerFields(int capacity) {
        super(capacity);
    }
}

abstract class SpscArrayQueueL2Pad<E> extends SpscArrayQueueProducerFields<E> {
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpscArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class SpscArrayQueueConsumerField<E> extends SpscArrayQueueL2Pad<E> {
    protected long consumerIndex;

    public SpscArrayQueueConsumerField(int capacity) {
        super(capacity);
    }
}

abstract class SpscArrayQueueL3Pad<E> extends SpscArrayQueueConsumerField<E> {
    long p40, p41, p42, p43, p44, p45, p46;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpscArrayQueueL3Pad(int capacity) {
        super(capacity);
    }
}

/**
 * A Single-Producer-Single-Consumer queue backed by a pre-allocated buffer.</br> This implementation is a mashup of the
 * <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a> algorithm with an optimization of the offer
 * method taken from the <a href="http://staff.ustc.edu.cn/~bhua/publications/IJPP_draft.pdf">BQueue</a> algorithm (a
 * variation on Fast Flow).<br>
 * This implementation is wait free.
 * 
 * @author nitsanw
 * 
 * @param <E>
 */
public final class SpscArrayQueue<E> extends SpscArrayQueueL3Pad<E> {
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);

    public SpscArrayQueue(final int capacity) {
        super(Math.max(capacity, 2 * OFFER_BATCH_SIZE));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        // local load of field to avoid repeated loads after volatile reads
        final E[] lElementBuffer = buffer;
        if (producerIndex >= producerLookAhead) {
            if (null != lvElement(lElementBuffer, calcElementOffset(producerIndex + OFFER_BATCH_SIZE))) {// LoadLoad
                return false;
            }
            producerLookAhead = producerIndex + OFFER_BATCH_SIZE;
        }
        soElement(lElementBuffer, calcElementOffset(producerIndex), e);// StoreStore
        producerIndex++;

        return true;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E poll() {
        final long offset = calcElementOffset(consumerIndex);
        // local load of field to avoid repeated loads after volatile reads
        final E[] lElementBuffer = buffer;
        final E e = lvElement(lElementBuffer, offset);// LoadLoad
        if (null == e) {
            return null;
        }
        soElement(lElementBuffer, offset, null);// StoreStore
        consumerIndex++;
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E peek() {
        return lvElement(calcElementOffset(consumerIndex));
    }

    @Override
    public int size() {
        if(peek() == null) {// LoadLoad -> will force load of indexes from memory.
            return 0;
        }
        return (int) (producerIndex - consumerIndex);
    }
}
