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
package org.jctools.queues.alt;

import org.jctools.util.UnsafeAccess;

abstract class ProducerFields<E> extends ConcurrentCircularArray<E> {
    protected static final long TAIL_OFFSET;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE
                    .objectFieldOffset(ProducerFields.class.getDeclaredField("tail"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    protected long tail;
    protected long batchTail;

    public ProducerFields(ConcurrentCircularArray<E> c) {
        super(c);
    }
}

final class Producer<E> extends ProducerFields<E> implements ConcurrentQueueProducer<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public Producer(ConcurrentCircularArray<E> c) {
        super(c);
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        final E[] lb = buffer;
        if (tail >= batchTail) {
            if (null == lvElement(lb, calcOffset(tail + SpscArrayConcurrentQueue.OFFER_BATCH_SIZE))) {
                batchTail = tail + SpscArrayConcurrentQueue.OFFER_BATCH_SIZE;
            } else if (null != lvElement(lb, calcOffset(tail))) {
                return false;
            }
        }
        soElement(lb, calcOffset(tail), e);
        tail++;

        return true;
    }

    long getTailForSize() {
        return Math.max(UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET), tail);
    }
}

abstract class ConsumerFields<E> extends ConcurrentCircularArray<E> {
    protected static final long HEAD_OFFSET;
    static {
        try {
            HEAD_OFFSET = UnsafeAccess.UNSAFE
                    .objectFieldOffset(ConsumerFields.class.getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    protected long head = 0;

    public ConsumerFields(ConcurrentCircularArray<E> c) {
        super(c);
    }
}

final class Consumer<E> extends ConsumerFields<E> implements ConcurrentQueueConsumer<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    Consumer(ConcurrentCircularArray<E> c) {
        super(c);
    }

    @Override
    public E poll() {
        final long offset = calcOffset(head);
        final E[] lb = buffer;
        final E e = lvElement(lb, offset);
        if (null == e) {
            return null;
        }
        soElement(lb, offset, null);
        head++;
        return e;
    }

    @Override
    public E peek() {
        return lpElement(calcOffset(head));
    }

    @Override
    public void clear() {
        while (null != poll())
            ;
    }

    long getHeadForSize() {
        return Math.max(UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET), head);
    }
}

abstract class SpscArrayConcurrentQueueColdFields<E> extends ConcurrentCircularArray<E> {
    protected final Consumer<E> consumer;
    protected final Producer<E> producer;

    public SpscArrayConcurrentQueueColdFields(int capacity) {
        super(capacity);
        consumer = new Consumer<E>(this);
        producer = new Producer<E>(this);
    }
}

public final class SpscArrayConcurrentQueue<E> extends SpscArrayConcurrentQueueColdFields<E> implements
        ConcurrentQueue<E> {
    // Layout field/data offsets are runtime constants
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);
    // post pad queue fields
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpscArrayConcurrentQueue(final int capacity) {
        super(Math.max(capacity, 2 * OFFER_BATCH_SIZE));
    }

    @Override
    public int size() {
        long headForSize = consumer.getHeadForSize();
        long tailForSize = producer.getTailForSize();
        return (int) (tailForSize - headForSize);
    }

    @Override
    public int capacity() {
        return (int) mask + 1;
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        return consumer;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        return producer;
    }
}
